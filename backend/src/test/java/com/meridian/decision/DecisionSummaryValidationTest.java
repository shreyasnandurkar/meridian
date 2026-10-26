package com.meridian.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.meridian.decision.DecisionSummaryWriter.Output;
import org.junit.jupiter.api.Test;

/** A6 output checks: the count must be stated as given, and no number may be invented. */
class DecisionSummaryValidationTest {

    private static final String FACTS = """
            decision: change approved
            change: Move Fort Aguada from day 2 to day 4
            quorum: 3 of 4 members must approve
            approved (3 of 4): Meera, Asha, Dev
            rejected (1 of 4): Kabir""";
    private static final Tally TALLY = new Tally(3, 1, 3, 4);

    @Test
    void acceptsAGroundedSummary() {
        assertThat(DecisionSummaryWriter.validate(new Output(
                "Moved Fort Aguada from day 2 to day 4: 3 of 4 approved. Kabir voted against."), FACTS, TALLY))
                .isEmpty();
        assertThat(DecisionSummaryWriter.validate(new Output("Fort Aguada moves to day 4 (3/4 votes)."), FACTS,
                TALLY)).isEmpty();
    }

    @Test
    void rejectsAMissingCount() {
        assertThat(DecisionSummaryWriter.validate(new Output("Most of the group moved Fort Aguada to day 4."), FACTS,
                TALLY)).anyMatch(e -> e.contains("count"));
    }

    @Test
    void rejectsInventedNumbers() {
        assertThat(DecisionSummaryWriter.validate(new Output(
                "3 of 4 approved moving Fort Aguada, saving 45 minutes of driving."), FACTS, TALLY))
                .containsExactly("the number 45 is not in the facts");
    }

    @Test
    void rejectsEmptyAndOverlongText() {
        assertThat(DecisionSummaryWriter.validate(new Output(" "), FACTS, TALLY)).containsExactly("summary is empty");
        assertThat(DecisionSummaryWriter.validate(new Output("3 of 4 approved. " + "a".repeat(300)), FACTS, TALLY))
                .anyMatch(e -> e.contains("longer than"));
    }
}
