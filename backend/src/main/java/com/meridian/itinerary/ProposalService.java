package com.meridian.itinerary;

import com.meridian.common.ApiException;
import com.meridian.itinerary.ItineraryDtos.ProposalView;
import com.meridian.itinerary.ItineraryDtos.ProposeRequest;
import com.meridian.shortlist.ShortlistDtos.Person;
import com.meridian.trip.TripAccess;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Members propose changes to the itinerary (spec §5.7): move, swap, remove or add a stop, with a reason. The summary
 * is written by code from the edit. Voting on proposals, and applying approved ones, comes with the consensus gate.
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
    private final UserRepository users;

    public ProposalService(TripAccess access, ItineraryRepository itineraries, ItineraryDayRepository days,
            ItineraryItemRepository items, ProposalRepository proposals, CommentRepository comments,
            StopFactory stopFactory, UserRepository users) {
        this.access = access;
        this.itineraries = itineraries;
        this.days = days;
        this.items = items;
        this.proposals = proposals;
        this.comments = comments;
        this.stopFactory = stopFactory;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<ProposalView> list(UUID tripId, UUID userId) {
        access.requireMember(tripId, userId);
        Itinerary itinerary = itineraries.findByTripId(tripId).orElse(null);
        if (itinerary == null) return List.of();
        Context ctx = context(tripId, itinerary);
        List<Proposal> list = proposals.findByTripIdOrderByCreatedAtDesc(tripId);
        Map<UUID, String> names = names(list.stream().map(Proposal::getCreatedBy).collect(Collectors.toSet()));
        Map<UUID, Long> commentCounts = comments.findByTripIdOrderByCreatedAt(tripId).stream()
                .filter(c -> c.getTargetType() == Comment.Target.PROPOSAL)
                .collect(Collectors.groupingBy(Comment::getTargetId, Collectors.counting()));
        return list.stream().map(p -> view(p, itinerary, ctx, names, userId,
                commentCounts.getOrDefault(p.getId(), 0L).intValue())).toList();
    }

    @Transactional
    public ProposalView propose(UUID tripId, UUID userId, ProposeRequest req) {
        access.requireMember(tripId, userId);
        Itinerary itinerary = itineraries.findByTripId(tripId)
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
        return view(saved, itinerary, ctx, names(Set.of(userId)), userId, 0);
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
        Itinerary itinerary = itineraries.findById(p.getItineraryId()).orElseThrow();
        return view(p, itinerary, context(tripId, itinerary), names(Set.of(userId)), userId, 0);
    }

    private record Context(Layout layout, StopFactory.TripStops stops) {}

    private Context context(UUID tripId, Itinerary itinerary) {
        Layout layout = ItineraryStore.layoutOf(days.findByItineraryIdOrderByDayIndex(itinerary.getId()),
                items.findByItineraryIdOrderByPosition(itinerary.getId()));
        return new Context(layout, stopFactory.load(tripId, false));
    }

    private ProposalView view(Proposal p, Itinerary itinerary, Context ctx, Map<UUID, String> names, UUID viewer,
            int commentCount) {
        List<String> problems = p.isOpen() ? ctx.layout().check(p.getPayload(), ctx.stops().stops().keySet())
                : List.of();
        return new ProposalView(p.getId(), p.getType(), p.getPayload(), p.getSummary(), p.getReason(), p.getStatus(),
                new Person(p.getCreatedBy(), names.getOrDefault(p.getCreatedBy(), "Former member")), p.getCreatedAt(),
                p.getResolvedAt(), p.getBaseVersion() == itinerary.getVersion(), problems,
                p.isOpen() && p.getCreatedBy().equals(viewer), commentCount);
    }

    private Map<UUID, String> names(Set<UUID> ids) {
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getDisplayName));
    }
}
