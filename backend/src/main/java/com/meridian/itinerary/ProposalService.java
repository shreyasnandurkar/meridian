package com.meridian.itinerary;

import com.meridian.common.ApiException;
import com.meridian.decision.ConsensusService;
import com.meridian.decision.DecisionEntry;
import com.meridian.decision.DecisionRepository;
import com.meridian.decision.Tally;
import com.meridian.decision.Vote;
import com.meridian.decision.VoteRepository;
import com.meridian.itinerary.ItineraryDtos.ProposalView;
import com.meridian.itinerary.ItineraryDtos.ProposeRequest;
import com.meridian.itinerary.ItineraryDtos.VoteView;
import com.meridian.realtime.TripEvents;
import com.meridian.shortlist.ShortlistDtos.Person;
import com.meridian.trip.TripAccess;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Members propose changes to the itinerary (spec §5.7): move, swap, remove or add a stop, with a reason. The summary
 * is written by code from the edit. Proposing counts as the proposer's approval; the group votes on the rest
 * ({@link ConsensusService}), which applies the change once the quorum approves.
 */
@Service
public class ProposalService {

    private final TripAccess access;
    private final ItineraryRepository itineraries;
    private final ItineraryDayRepository days;
    private final ItineraryItemRepository items;
    private final ProposalRepository proposals;
    private final CommentRepository comments;
    private final StopFactory stopFactory;
    private final VoteRepository votes;
    private final DecisionRepository decisions;
    private final ConsensusService consensus;
    private final TripEvents events;

    public ProposalService(TripAccess access, ItineraryRepository itineraries, ItineraryDayRepository days,
            ItineraryItemRepository items, ProposalRepository proposals, CommentRepository comments,
            StopFactory stopFactory, VoteRepository votes, DecisionRepository decisions, ConsensusService consensus,
            TripEvents events) {
        this.access = access;
        this.itineraries = itineraries;
        this.days = days;
        this.items = items;
        this.proposals = proposals;
        this.comments = comments;
        this.stopFactory = stopFactory;
        this.votes = votes;
        this.decisions = decisions;
        this.consensus = consensus;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<ProposalView> list(UUID tripId, UUID userId) {
        access.requireMember(tripId, userId);
        Itinerary itinerary = itineraries.findByTripId(tripId).orElse(null);
        if (itinerary == null) return List.of();
        return views(tripId, itinerary, proposals.findByTripIdOrderByCreatedAtDesc(tripId), userId);
    }

    @Transactional(readOnly = true)
    public ProposalView get(UUID tripId, UUID userId, UUID proposalId) {
        access.requireMember(tripId, userId);
        Proposal p = proposals.findByIdAndTripId(proposalId, tripId)
                .orElseThrow(() -> ApiException.notFound("Proposal not found"));
        Itinerary itinerary = itineraries.findById(p.getItineraryId()).orElseThrow();
        return views(tripId, itinerary, List.of(p), userId).getFirst();
    }

    @Transactional
    public ProposalView propose(UUID tripId, UUID userId, ProposeRequest req) {
        access.requireMember(tripId, userId);
        Itinerary itinerary = itineraries.lockByTripId(tripId)
                .orElseThrow(() -> ApiException.notFound("This trip has no itinerary yet"));
        ItineraryStore.requireDraft(itinerary);
        Context ctx = context(tripId, itinerary);
        PlanEdit edit = req.edit();
        List<String> problems = ctx.layout().check(edit, ctx.stops().stops().keySet());
        if (!problems.isEmpty()) throw ApiException.conflict(String.join(" ", problems));
        boolean duplicate = proposals.findByItineraryIdAndStatus(itinerary.getId(), Proposal.Status.OPEN).stream()
                .anyMatch(p -> p.getPayload().equals(edit));
        if (duplicate) throw ApiException.conflict("The same change has already been proposed.");
        String summary = ctx.layout().describe(edit, id -> ctx.stops().titles().getOrDefault(id, "a place"));
        Proposal saved = proposals.save(new Proposal(tripId, itinerary, edit, summary, req.reason(), userId));
        events.publish(tripId, TripEvents.Type.PROPOSALS, userId, Map.of("proposalId", saved.getId()));
        consensus.cast(tripId, userId, saved, itinerary, Vote.Value.APPROVE);
        return views(tripId, itinerary, List.of(saved), userId).getFirst();
    }

    /** Only the person who proposed it can withdraw an open proposal. */
    @Transactional
    public ProposalView withdraw(UUID tripId, UUID userId, UUID proposalId) {
        access.requireMember(tripId, userId);
        Proposal p = proposals.findByIdAndTripId(proposalId, tripId)
                .orElseThrow(() -> ApiException.notFound("Proposal not found"));
        if (!p.getCreatedBy().equals(userId)) throw ApiException.forbidden("Only its author can withdraw a proposal");
        if (!p.isOpen()) throw ApiException.conflict("This proposal is already closed");
        p.resolve(Proposal.Status.WITHDRAWN);
        events.publish(tripId, TripEvents.Type.PROPOSALS, userId, Map.of("proposalId", proposalId));
        Itinerary itinerary = itineraries.findById(p.getItineraryId()).orElseThrow();
        return views(tripId, itinerary, List.of(p), userId).getFirst();
    }

    private record Context(Layout layout, StopFactory.TripStops stops) {}

    private Context context(UUID tripId, Itinerary itinerary) {
        Layout layout = ItineraryStore.layoutOf(days.findByItineraryIdOrderByDayIndex(itinerary.getId()),
                items.findByItineraryIdOrderByPosition(itinerary.getId()));
        return new Context(layout, stopFactory.load(tripId, false));
    }

    private List<ProposalView> views(UUID tripId, Itinerary itinerary, List<Proposal> list, UUID viewer) {
        if (list.isEmpty()) return List.of();
        Context ctx = context(tripId, itinerary);
        ConsensusService.Group group = consensus.group(tripId);
        Map<UUID, List<Vote>> byProposal = votes.findByProposalIdIn(list.stream().map(Proposal::getId).toList())
                .stream().sorted(Comparator.comparing(Vote::getVotedAt))
                .collect(Collectors.groupingBy(Vote::getProposalId));
        Map<UUID, Long> commentCounts = comments.findByTripIdOrderByCreatedAt(tripId).stream()
                .filter(c -> c.getTargetType() == Comment.Target.PROPOSAL)
                .collect(Collectors.groupingBy(Comment::getTargetId, Collectors.counting()));
        Map<UUID, UUID> decisionOf = decisions.findByTripIdOrderBySeqDesc(tripId).stream()
                .filter(d -> d.getProposalId() != null)
                .collect(Collectors.toMap(DecisionEntry::getProposalId, DecisionEntry::getId, (a, b) -> a));
        List<ProposalView> out = new ArrayList<>();
        for (Proposal p : list) {
            List<Vote> cast = byProposal.getOrDefault(p.getId(), List.of());
            out.add(view(p, itinerary, ctx, group, cast, viewer, commentCounts.getOrDefault(p.getId(), 0L).intValue(),
                    decisionOf.get(p.getId())));
        }
        return out;
    }

    private static ProposalView view(Proposal p, Itinerary itinerary, Context ctx, ConsensusService.Group group,
            List<Vote> cast, UUID viewer, int commentCount, UUID decisionId) {
        List<String> problems = p.isOpen() ? ctx.layout().check(p.getPayload(), ctx.stops().stops().keySet())
                : List.of();
        List<VoteView> voteViews = cast.stream()
                .map(v -> new VoteView(new Person(v.getUserId(), group.name(v.getUserId())), v.getValue(),
                        v.getVotedAt()))
                .toList();
        Set<UUID> voted = cast.stream().map(Vote::getUserId).collect(Collectors.toSet());
        List<Person> notVoted = group.members().stream().filter(m -> !voted.contains(m.getUserId()))
                .map(m -> new Person(m.getUserId(), group.name(m.getUserId()))).toList();
        Tally tally = ConsensusService.tally(cast, group);
        Vote.Value mine = cast.stream().filter(v -> v.getUserId().equals(viewer)).map(Vote::getValue).findFirst()
                .orElse(null);
        boolean canVote = p.isOpen() && !itinerary.isLocked() && group.isMember(viewer);
        return new ProposalView(p.getId(), p.getType(), p.getPayload(), p.getSummary(), p.getReason(), p.getStatus(),
                new Person(p.getCreatedBy(), group.name(p.getCreatedBy())), p.getCreatedAt(), p.getResolvedAt(),
                p.getBaseVersion() == itinerary.getVersion(), problems, p.isOpen() && p.getCreatedBy().equals(viewer),
                commentCount, voteViews, tally, notVoted, mine, canVote, decisionId);
    }
}
