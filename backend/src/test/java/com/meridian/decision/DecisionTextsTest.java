package com.meridian.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.meridian.decision.DecisionEntry.CastVote;
import com.meridian.decision.DecisionEntry.Kind;
import com.meridian.decision.DecisionEntry.Member;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DecisionTextsTest {

    private static CastVote vote(String name, Vote.Value value) {
        return new CastVote(UUID.randomUUID(), name, value, Instant.now());
    }

    private static final List<CastVote> VOTES = List.of(vote("Meera", Vote.Value.APPROVE),
            vote("Kabir", Vote.Value.REJECT), vote("Asha", Vote.Value.APPROVE), vote("Dev", Vote.Value.APPROVE));

    private static DecisionTexts.Context change(Kind kind, List<CastVote> votes, Tally tally, List<Member> silent,
            String reason, List<String> comments) {
        return new DecisionTexts.Context(kind, "Move Fort Aguada from day 2 to day 4",
                List.of("Move Fort Aguada from day 2 to day 4", "Keep the plan as it was"), tally, votes, silent,
                "Meera", null, reason, comments);
    }

    @Test
    void approvedChangeNamesTheCountTheDissentAndTheReason() {
        String s = DecisionTexts.template(change(Kind.CHANGE_APPROVED, VOTES, new Tally(3, 1, 3, 4), List.of(),
                "Day 2 is long", List.of()));
        assertThat(s).isEqualTo("Chose to move Fort Aguada from day 2 to day 4 over keeping the plan as it was: "
                + "3 of 4 approved (3 needed). Voted against: Kabir. Meera's reason: “Day 2 is long”.");
    }

    @Test
    void rejectedChangeExplainsWhyTheQuorumIsOutOfReach() {
        List<CastVote> votes = List.of(vote("Meera", Vote.Value.APPROVE), vote("Kabir", Vote.Value.REJECT),
                vote("Dev", Vote.Value.REJECT));
        String s = DecisionTexts.template(change(Kind.CHANGE_REJECTED, votes, new Tally(1, 2, 3, 4),
                List.of(new Member(UUID.randomUUID(), "Asha")), null, List.of()));
        assertThat(s).contains("Kept the plan as it was instead of: Move Fort Aguada")
                .contains("2 of 4 voted against, so the 3 approvals needed could no longer be reached")
                .contains("In favour: Meera.");
    }

    @Test
    void lockOverrideQuotesTheOrganizersReason() {
        DecisionTexts.Context c = new DecisionTexts.Context(Kind.LOCK_OVERRIDE,
                DecisionTexts.title(Kind.LOCK_OVERRIDE, "12 stops over 5 days"), List.of("Lock", "Keep"),
                new Tally(1, 0, 3, 4), List.of(vote("Asha", Vote.Value.APPROVE)), List.of(), null, "Asha",
                "  Booking   deadline\ntonight ", List.of());
        assertThat(DecisionTexts.template(c)).isEqualTo("Asha locked the itinerary without the group's quorum "
                + "(1 of 4 approved, 3 needed). Reason: “Booking deadline tonight”.");
    }

    @Test
    void factsKeepEachPersonWithTheirVoteAndSpellCountsAsGiven() {
        String facts = DecisionTexts.facts(change(Kind.CHANGE_APPROVED, VOTES, new Tally(3, 1, 3, 4), List.of(),
                "Day 2 is long", List.of("Kabir: “Sunset on day 2”")));
        assertThat(facts.lines()).containsExactly(
                "decision: change approved",
                "change: Move Fort Aguada from day 2 to day 4",
                "alternatives: Move Fort Aguada from day 2 to day 4 | Keep the plan as it was",
                "proposed by: Meera",
                "reason given by Meera: “Day 2 is long”",
                "quorum: 3 of 4 members must approve",
                "approved (3 of 4): Meera, Asha, Dev",
                "rejected (1 of 4): Kabir",
                "did not vote: nobody",
                "comments: Kabir: “Sunset on day 2”");
    }

    @Test
    void longTextIsClipped() {
        String reason = "x".repeat(400);
        assertThat(DecisionTexts.quote(reason)).hasSize(202).endsWith("…”");
        assertThat(DecisionTexts.clip("y".repeat(700), DecisionTexts.SUMMARY_MAX)).hasSize(DecisionTexts.SUMMARY_MAX);
    }
}
