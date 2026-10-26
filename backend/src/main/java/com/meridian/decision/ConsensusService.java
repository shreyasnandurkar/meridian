package com.meridian.decision;

import com.meridian.common.ApiException;
import com.meridian.decision.DecisionDtos.GateView;
import com.meridian.decision.DecisionDtos.PlanVoter;
import com.meridian.decision.DecisionEntry.CastVote;
import com.meridian.decision.DecisionEntry.Kind;
import com.meridian.decision.DecisionEntry.Member;
import com.meridian.itinerary.Itinerary;
import com.meridian.itinerary.ItineraryItem;
import com.meridian.itinerary.ItineraryItemRepository;
import com.meridian.itinerary.ItineraryRepository;
import com.meridian.itinerary.ItineraryStore;
import com.meridian.itinerary.Proposal;
import com.meridian.itinerary.ProposalRepository;
import com.meridian.realtime.TripEvents;
import com.meridian.shortlist.ShortlistDtos.Person;
import com.meridian.trip.Quorum;
import com.meridian.trip.Trip;
import com.meridian.trip.TripAccess;
import com.meridian.trip.TripMember;
import com.meridian.trip.TripMemberRepository;
import com.meridian.trip.TripRepository;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Voting and the consensus gate (spec §5.8). Every member has one equal vote. A change proposal is applied by code
 * as soon as the quorum approves it and closed as soon as the quorum is out of reach. The itinerary can be locked
 * when the quorum approves the current plan; the organizer can lock without it only by giving a reason. Every
 * outcome is appended to the decision log. All of it runs under the itinerary's row lock, one decision at a time.
 */
@Service
public class ConsensusService {

    static final String KEEP_PLAN = "Keep the plan as it was";
    static final String KEEP_DRAFT = "Keep it as a draft";

    private final TripAccess access;
    private final TripRepository trips;
    private final TripMemberRepository members;
    private final UserRepository users;
    private final ItineraryRepository itineraries;
    private final ItineraryItemRepository items;
    private final ItineraryStore store;
    private final ProposalRepository proposals;
    private final VoteRepository votes;
    private final PlanApprovalRepository approvals;
    private final DecisionRepository decisionLog;
    private final DecisionService decisions;
    private final TripEvents events;

    public ConsensusService(TripAccess access, TripRepository trips, TripMemberRepository members,
            UserRepository users, ItineraryRepository itineraries, ItineraryItemRepository items, ItineraryStore store,
            ProposalRepository proposals, VoteRepository votes, PlanApprovalRepository approvals,
            DecisionRepository decisionLog, DecisionService decisions, TripEvents events) {
        this.access = access;
        this.trips = trips;
        this.members = members;
        this.users = users;
        this.itineraries = itineraries;
        this.items = items;
        this.store = store;
        this.proposals = proposals;
        this.votes = votes;
        this.approvals = approvals;
        this.decisionLog = decisionLog;
        this.decisions = decisions;
        this.events = events;
    }

    /** The trip's members, their names and the approvals the quorum needs right now. */
    public record Group(Trip trip, List<TripMember> members, Map<UUID, String> names) {

        public int size() {
            return members.size();
        }

        public int required() {
            return Quorum.requiredApprovals(size(), trip.getQuorumMinApprovals());
        }

        public boolean isMember(UUID userId) {
            return names.containsKey(userId);
        }

        public String name(UUID userId) {
            return names.getOrDefault(userId, "Former member");
        }

        public List<Member> without(Set<UUID> voted) {
            return members.stream().filter(m -> !voted.contains(m.getUserId()))
                    .map(m -> new Member(m.getUserId(), name(m.getUserId()))).toList();
        }
    }

    @Transactional(readOnly = true)
    public Group group(UUID tripId) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> ApiException.notFound("Trip not found"));
        List<TripMember> list = members.findByIdTripIdOrderByJoinedAt(tripId);
        Map<UUID, String> byId = users.findAllById(list.stream().map(TripMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));
        Map<UUID, String> names = new LinkedHashMap<>();
        list.forEach(m -> names.put(m.getUserId(), byId.getOrDefault(m.getUserId(), "Member")));
        return new Group(trip, list, names);
    }

    /** Votes counted against the quorum: only current members' votes count. */
    public static Tally tally(List<Vote> cast, Group group) {
        return Tally.of(cast.stream().filter(v -> group.isMember(v.getUserId())).map(Vote::getValue).toList(),
                group.required(), group.size());
    }

    // ---- change proposals ------------------------------------------------------------------------------------

    /** Approve or reject an open proposal (any member; a second vote replaces the first). */
    @Transactional
    public void vote(UUID tripId, UUID userId, UUID proposalId, Vote.Value value) {
        access.requireMember(tripId, userId);
        Itinerary itinerary = lockItinerary(tripId);
        Proposal p = proposals.findByIdAndTripId(proposalId, tripId)
                .orElseThrow(() -> ApiException.notFound("Proposal not found"));
        cast(tripId, userId, p, itinerary, value);
    }

    /**
     * Records a vote and settles the proposal if the vote decided it. The proposer's own approval goes through
     * here when a proposal is made.
     */
    @Transactional
    public void cast(UUID tripId, UUID userId, Proposal p, Itinerary itinerary, Vote.Value value) {
        if (!p.isOpen()) throw ApiException.conflict("This proposal is already closed");
        ItineraryStore.requireDraft(itinerary);
        if (value == Vote.Value.APPROVE) {
            List<String> problems = store.check(tripId, p.getPayload());
            if (!problems.isEmpty()) {
                throw ApiException.conflict("This change no longer fits the plan: " + String.join(" ", problems));
            }
        }
        Vote vote = votes.findByProposalIdAndUserId(p.getId(), userId).orElse(null);
        if (vote == null) votes.save(new Vote(p.getId(), userId, value));
        else vote.change(value);
        votes.flush();
        events.publish(tripId, TripEvents.Type.VOTES, userId, Map.of("proposalId", p.getId()));
        settle(tripId, p, itinerary, group(tripId), userId);
    }

    /** Takes back a vote on an open proposal. */
    @Transactional
    public void clearVote(UUID tripId, UUID userId, UUID proposalId) {
        access.requireMember(tripId, userId);
        lockItinerary(tripId);
        Proposal p = proposals.findByIdAndTripId(proposalId, tripId)
                .orElseThrow(() -> ApiException.notFound("Proposal not found"));
        if (!p.isOpen()) throw ApiException.conflict("This proposal is already closed");
        votes.findByProposalIdAndUserId(proposalId, userId).ifPresent(votes::delete);
        events.publish(tripId, TripEvents.Type.VOTES, userId, Map.of("proposalId", proposalId));
    }

    /** After the quorum changed: settle every open proposal the new rule decides. */
    @Transactional
    public void settleOpen(UUID tripId, UUID actorId) {
        Itinerary itinerary = itineraries.lockByTripId(tripId).orElse(null);
        if (itinerary == null || itinerary.isLocked()) return;
        Group group = group(tripId);
        for (Proposal p : proposals.findByItineraryIdAndStatus(itinerary.getId(), Proposal.Status.OPEN)) {
            if (p.isOpen()) settle(tripId, p, itinerary, group, actorId);
        }
    }

    private void settle(UUID tripId, Proposal p, Itinerary itinerary, Group group, UUID actorId) {
        List<Vote> cast = votes.findByProposalIdOrderByVotedAt(p.getId());
        Tally tally = tally(cast, group);
        switch (tally.outcome()) {
            case OPEN -> { }
            case APPROVED -> {
                if (!store.check(tripId, p.getPayload()).isEmpty()) return; // can't apply; it waits for changes
                int before = itinerary.getVersion();
                store.applyEdit(tripId, p.getPayload(), "Proposed by " + group.name(p.getCreatedBy())
                        + " and approved by " + tally.approvals() + " of " + tally.members()
                        + "; code recomputed the times.");
                p.resolve(Proposal.Status.APPROVED);
                carryPlanApprovals(itinerary, before, cast);
                decisions.record(tripId, changeDraft(Kind.CHANGE_APPROVED, p, cast, tally, group, itinerary), actorId);
                events.publish(tripId, TripEvents.Type.ITINERARY, actorId);
                events.publish(tripId, TripEvents.Type.APPROVAL, actorId);
            }
            case REJECTED -> {
                p.resolve(Proposal.Status.REJECTED);
                decisions.record(tripId, changeDraft(Kind.CHANGE_REJECTED, p, cast, tally, group, itinerary), actorId);
            }
        }
        if (!p.isOpen()) events.publish(tripId, TripEvents.Type.PROPOSALS, actorId, Map.of("proposalId", p.getId()));
    }

    /**
     * A change the member voted for keeps their approval of the plan; everyone else's verdict is now on an older
     * version and has to be given again.
     */
    private void carryPlanApprovals(Itinerary itinerary, int before, List<Vote> cast) {
        Set<UUID> approvedChange = cast.stream().filter(v -> v.getValue() == Vote.Value.APPROVE)
                .map(Vote::getUserId).collect(Collectors.toSet());
        for (PlanApproval a : approvals.findByIdItineraryId(itinerary.getId())) {
            if (a.getVersion() == before && a.getValue() == Vote.Value.APPROVE
                    && approvedChange.contains(a.getUserId())) {
                a.carryTo(itinerary.getVersion());
            }
        }
    }

    private DecisionEntry.Draft changeDraft(Kind kind, Proposal p, List<Vote> cast, Tally tally, Group group,
            Itinerary itinerary) {
        List<CastVote> recorded = cast.stream().filter(v -> group.isMember(v.getUserId()))
                .map(v -> new CastVote(v.getUserId(), group.name(v.getUserId()), v.getValue(), v.getVotedAt()))
                .toList();
        List<Member> silent = group.without(cast.stream().map(Vote::getUserId).collect(Collectors.toSet()));
        List<UUID> itemIds = new ArrayList<>();
        itemIds.add(p.getPayload().itemId());
        if (p.getPayload().otherItemId() != null) itemIds.add(p.getPayload().otherItemId());
        DecisionTexts.Context context = new DecisionTexts.Context(kind, p.getSummary(),
                List.of(p.getSummary(), KEEP_PLAN), tally, recorded, silent, group.name(p.getCreatedBy()), null,
                p.getReason(), List.of());
        return new DecisionEntry.Draft(kind, p.getSummary(), context.alternatives(), recorded, tally, silent,
                p.getId(), p.getReason(), itinerary.getVersion(), itemIds, null, DecisionTexts.template(context));
    }

    // ---- the plan and the consensus gate ----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public GateView gate(UUID tripId, UUID userId) {
        TripMember me = access.requireMember(tripId, userId);
        Itinerary itinerary = itineraries.findByTripId(tripId)
                .orElseThrow(() -> ApiException.notFound("This trip has no itinerary yet"));
        Group group = group(tripId);
        Map<UUID, PlanApproval> byUser = approvals.findByIdItineraryId(itinerary.getId()).stream()
                .collect(Collectors.toMap(PlanApproval::getUserId, Function.identity()));
        Tally tally = planTally(itinerary, byUser.values(), group);
        List<PlanVoter> voters = new ArrayList<>();
        List<Person> notVoted = new ArrayList<>();
        for (TripMember m : group.members()) {
            PlanApproval a = byUser.get(m.getUserId());
            boolean current = a != null && a.getVersion() == itinerary.getVersion();
            voters.add(new PlanVoter(m.getUserId(), group.name(m.getUserId()), current ? a.getValue() : null,
                    a != null && !current ? a.getValue() : null, a == null ? null : a.getVotedAt()));
            if (!current) notVoted.add(new Person(m.getUserId(), group.name(m.getUserId())));
        }
        PlanApproval mine = byUser.get(userId);
        boolean draft = !itinerary.isLocked();
        int open = proposals.findByItineraryIdAndStatus(itinerary.getId(), Proposal.Status.OPEN).size();
        UUID lockDecision = itinerary.isLocked() ? decisionLog.findByTripIdOrderBySeqDesc(tripId).stream()
                .filter(d -> d.getKind() == Kind.PLAN_LOCKED || d.getKind() == Kind.LOCK_OVERRIDE)
                .map(DecisionEntry::getId).findFirst().orElse(null) : null;
        Person lockedBy = itinerary.getLockedBy() == null ? null
                : new Person(itinerary.getLockedBy(), group.name(itinerary.getLockedBy()));
        return new GateView(itinerary.getStatus(), itinerary.getVersion(), tally, tally.quorumMet(), voters, notVoted,
                mine != null && mine.getVersion() == itinerary.getVersion() ? mine.getValue() : null, draft,
                draft && me.isOrganizer(), open, itinerary.getLockedAt(), lockedBy, lockDecision);
    }

    private static Tally planTally(Itinerary itinerary, java.util.Collection<PlanApproval> all, Group group) {
        return Tally.of(all.stream()
                .filter(a -> a.getVersion() == itinerary.getVersion() && group.isMember(a.getUserId()))
                .map(PlanApproval::getValue).toList(), group.required(), group.size());
    }

    /** A member's verdict on the plan as it is now (replaces their earlier one). */
    @Transactional
    public void votePlan(UUID tripId, UUID userId, Vote.Value value) {
        access.requireMember(tripId, userId);
        Itinerary itinerary = lockItinerary(tripId);
        ItineraryStore.requireDraft(itinerary);
        PlanApproval a = approvals.findById(new PlanApproval.Key(itinerary.getId(), userId))
                .orElseGet(() -> new PlanApproval(itinerary.getId(), userId));
        a.set(value, itinerary.getVersion());
        approvals.save(a);
        events.publish(tripId, TripEvents.Type.APPROVAL, userId);
    }

    @Transactional
    public void clearPlanVote(UUID tripId, UUID userId) {
        access.requireMember(tripId, userId);
        Itinerary itinerary = lockItinerary(tripId);
        ItineraryStore.requireDraft(itinerary);
        approvals.findById(new PlanApproval.Key(itinerary.getId(), userId)).ifPresent(approvals::delete);
        events.publish(tripId, TripEvents.Type.APPROVAL, userId);
    }

    /**
     * Organizer only. With the quorum's approval of the current plan this simply locks it; without, a reason is
     * required and the lock is logged as an override. Open proposals are closed.
     */
    @Transactional
    public void lock(UUID tripId, UUID userId, String reason) {
        access.requireOrganizer(tripId, userId);
        Itinerary itinerary = lockItinerary(tripId);
        if (itinerary.isLocked()) throw ApiException.conflict("The itinerary is already locked");
        Group group = group(tripId);
        List<PlanApproval> all = approvals.findByIdItineraryId(itinerary.getId());
        Tally tally = planTally(itinerary, all, group);
        String why = reason == null || reason.isBlank() ? null : reason.strip();
        boolean override = !tally.quorumMet();
        if (override && why == null) {
            throw ApiException.conflict("The plan has " + tally.approvals() + " of the " + tally.required()
                    + " approvals it needs. Wait for the group, or give a reason to lock it anyway.");
        }
        for (Proposal p : proposals.findByItineraryIdAndStatus(itinerary.getId(), Proposal.Status.OPEN)) {
            p.resolve(Proposal.Status.CLOSED);
        }
        List<ItineraryItem> stops = items.findByItineraryIdOrderByPosition(itinerary.getId());
        long dayCount = stops.stream().map(ItineraryItem::getDayId).distinct().count();
        String size = stops.size() + " stops over " + dayCount + (dayCount == 1 ? " day" : " days");
        Kind kind = override ? Kind.LOCK_OVERRIDE : Kind.PLAN_LOCKED;

        List<PlanApproval> current = all.stream()
                .filter(a -> a.getVersion() == itinerary.getVersion() && group.isMember(a.getUserId()))
                .sorted(Comparator.comparing(PlanApproval::getVotedAt)).toList();
        List<CastVote> recorded = current.stream()
                .map(a -> new CastVote(a.getUserId(), group.name(a.getUserId()), a.getValue(), a.getVotedAt()))
                .toList();
        List<Member> silent = group.without(current.stream().map(PlanApproval::getUserId).collect(Collectors.toSet()));
        String title = DecisionTexts.title(kind, size);
        List<String> alternatives = List.of("Lock this plan (" + size + ")", KEEP_DRAFT);
        DecisionTexts.Context context = new DecisionTexts.Context(kind, title, alternatives, tally, recorded, silent,
                null, group.name(userId), override ? why : null, List.of());
        itinerary.lock(userId);
        decisions.record(tripId, new DecisionEntry.Draft(kind, title, alternatives, recorded, tally, silent, null,
                override ? why : null, itinerary.getVersion(),
                stops.stream().map(ItineraryItem::getShortlistItemId).toList(), userId,
                DecisionTexts.template(context)), userId);
        events.publish(tripId, TripEvents.Type.ITINERARY, userId);
        events.publish(tripId, TripEvents.Type.APPROVAL, userId);
        events.publish(tripId, TripEvents.Type.PROPOSALS, userId);
    }

    /** Organizer only: back to a draft so it can change again; logged with the optional reason. */
    @Transactional
    public void unlock(UUID tripId, UUID userId, String reason) {
        access.requireOrganizer(tripId, userId);
        Itinerary itinerary = lockItinerary(tripId);
        if (!itinerary.isLocked()) throw ApiException.conflict("The itinerary is not locked");
        Group group = group(tripId);
        String why = reason == null || reason.isBlank() ? null : reason.strip();
        Tally tally = planTally(itinerary, approvals.findByIdItineraryId(itinerary.getId()), group);
        String title = DecisionTexts.title(Kind.PLAN_UNLOCKED, "");
        List<String> alternatives = List.of("Unlock to make changes", "Keep the plan locked");
        DecisionTexts.Context context = new DecisionTexts.Context(Kind.PLAN_UNLOCKED, title, alternatives, tally,
                List.of(), List.of(), null, group.name(userId), why, List.of());
        itinerary.unlock();
        decisions.record(tripId, new DecisionEntry.Draft(Kind.PLAN_UNLOCKED, title, alternatives, List.of(), tally,
                List.of(), null, why, itinerary.getVersion(), List.of(), userId, DecisionTexts.template(context)),
                userId);
        events.publish(tripId, TripEvents.Type.ITINERARY, userId);
        events.publish(tripId, TripEvents.Type.APPROVAL, userId);
    }

    private Itinerary lockItinerary(UUID tripId) {
        return itineraries.lockByTripId(tripId)
                .orElseThrow(() -> ApiException.notFound("This trip has no itinerary yet"));
    }
}
