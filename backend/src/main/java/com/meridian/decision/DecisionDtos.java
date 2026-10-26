package com.meridian.decision;

import com.meridian.decision.DecisionEntry.CastVote;
import com.meridian.decision.DecisionEntry.Member;
import com.meridian.itinerary.Itinerary;
import com.meridian.profile.ProfileModels.Provenance;
import com.meridian.shortlist.ShortlistDtos.Person;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DecisionDtos {

    private DecisionDtos() {}

    public record VoteRequest(@NotNull Vote.Value value) {}

    /** Lock with quorum needs no reason; locking without it (organizer override) does. Unlock takes an optional one. */
    public record ReasonRequest(@Size(max = 500) String reason) {}

    public record DecisionView(
            UUID id,
            int seq,
            DecisionEntry.Kind kind,
            String title,
            List<String> alternatives,
            List<CastVote> votes,
            Tally tally,
            List<Member> notVoted,
            UUID proposalId,
            String reason,
            Integer itineraryVersion,
            List<UUID> itemIds,
            Person decidedBy,
            Instant decidedAt,
            String summary,
            Provenance provenance,
            /* the AI is writing this entry's summary right now */
            boolean summaryPending,
            /* AI summaries can be regenerated or dismissed; unlock entries are template-only */
            boolean canSummarize) {}

    /** One member's verdict on the plan. {@code value} counts; {@code earlier} is a verdict on an older version. */
    public record PlanVoter(UUID userId, String displayName, Vote.Value value, Vote.Value earlier, Instant at) {}

    /** The consensus gate (spec §5.8): who approved the current plan and whether it may be locked. */
    public record GateView(
            Itinerary.Status status,
            int version,
            Tally tally,
            boolean quorumMet,
            List<PlanVoter> voters,
            List<Person> notVoted,
            Vote.Value myVote,
            boolean canVote,
            boolean canLock,
            int openProposals,
            Instant lockedAt,
            Person lockedBy,
            UUID lockDecisionId) {}
}
