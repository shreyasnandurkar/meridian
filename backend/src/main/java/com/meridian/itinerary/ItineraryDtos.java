package com.meridian.itinerary;

import com.meridian.decision.Tally;
import com.meridian.decision.Vote;
import com.meridian.place.PlaceCategory;
import com.meridian.profile.ProfileModels.Provenance;
import com.meridian.shortlist.ShortlistDtos.Person;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ItineraryDtos {

    private ItineraryDtos() {}

    /** Leave {@code pace} out to use the group's pace (the median member's). */
    public record GenerateRequest(Pace pace) {}

    public record StopView(
            UUID id,
            UUID shortlistItemId,
            String title,
            PlaceCategory category,
            Double lat,
            Double lng,
            String start,
            String end,
            int durationMin,
            /* from the previous stop on the same day */
            int travelMin,
            Double travelKm,
            String note,
            Person addedBy,
            String addedReason,
            int commentCount,
            /* the latest decision-log entry about this stop (a change the group approved, or the lock) */
            UUID decisionId) {}

    public record DayView(
            int index,
            LocalDate date,
            String summary,
            Provenance provenance,
            /* what code changed after planning: repaired times, stops left out, hand edits */
            List<String> repairs,
            String dayStart,
            String dayEnd,
            /* minutes the last stop runs past dayEnd; 0 when the day fits */
            int overflowMin,
            List<StopView> stops) {}

    public record UnplacedView(UUID shortlistItemId, String title, PlaceCategory category, String reason,
            boolean placeable) {}

    public record ItineraryView(
            UUID id,
            Itinerary.Status status,
            int version,
            Pace pace,
            Pace groupPace,
            Instant generatedAt,
            Person generatedBy,
            Instant updatedAt,
            List<DayView> days,
            List<UnplacedView> unplaced,
            int openProposals,
            boolean canEdit,
            boolean aiAvailable,
            Instant lockedAt,
            Person lockedBy) {}

    public record ProposeRequest(@NotNull @Valid PlanEdit edit, @Size(max = 500) String reason) {}

    public record ProposalView(
            UUID id,
            PlanEdit.Type type,
            PlanEdit edit,
            String summary,
            String reason,
            Proposal.Status status,
            Person createdBy,
            Instant createdAt,
            Instant resolvedAt,
            /* made against the itinerary as it is now */
            boolean current,
            /* why it could not be applied to the plan as it is now; empty when it can */
            List<String> problems,
            boolean canWithdraw,
            int commentCount,
            List<VoteView> votes,
            Tally tally,
            List<Person> notVoted,
            Vote.Value myVote,
            boolean canVote,
            /* the decision-log entry written when the vote closed */
            UUID decisionId) {}

    public record VoteView(Person voter, Vote.Value value, Instant at) {}

    public record CommentRequest(
            @NotNull Comment.Target targetType,
            @NotNull UUID targetId,
            @NotBlank @Size(max = 1000) String body) {}

    public record CommentView(UUID id, Comment.Target targetType, UUID targetId, Person author, String body,
            Instant createdAt, boolean canDelete) {}
}
