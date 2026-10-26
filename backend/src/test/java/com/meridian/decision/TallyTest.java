package com.meridian.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.meridian.decision.Tally.Outcome;
import com.meridian.trip.Quorum;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TallyTest {

    /** Votes as a string: A = approve, R = reject; the quorum is a simple majority unless {@code min} is set. */
    @ParameterizedTest(name = "{0} members, min {1}, votes \"{2}\" -> {3}")
    @CsvSource(nullValues = "null", value = {
        "4, null, '',     OPEN",
        "4, null, A,      OPEN",
        "4, null, AA,     OPEN",
        "4, null, AAA,    APPROVED",  // 3 of 4 is a majority
        "4, null, AAR,    OPEN",      // the last member can still approve
        "4, null, AR,     OPEN",
        "4, null, RR,     REJECTED",  // only 2 could approve, 3 are needed
        "4, null, AARR,   REJECTED",
        "5, null, AARR,   OPEN",      // the fifth vote decides
        "5, null, AAARR,  APPROVED",
        "2, null, A,      OPEN",      // a pair must agree
        "2, null, R,      REJECTED",
        "1, null, A,      APPROVED",
        "4, 2,    AR,     OPEN",
        "4, 2,    RA,     OPEN",
        "4, 2,    ARA,    APPROVED",  // the trip asks for 2 approvals
        "4, 4,    AAAR,   REJECTED",  // unanimity: one rejection ends it
        "4, 4,    AAA,    OPEN",
    })
    void outcome(int members, Integer min, String votes, Outcome expected) {
        List<Vote.Value> cast = new ArrayList<>();
        for (char c : votes.strip().toCharArray()) cast.add(c == 'A' ? Vote.Value.APPROVE : Vote.Value.REJECT);
        Tally tally = Tally.of(cast, Quorum.requiredApprovals(members, min), members);
        assertThat(tally.outcome()).isEqualTo(expected);
        assertThat(tally.quorumMet()).isEqualTo(expected == Outcome.APPROVED);
    }

    @ParameterizedTest
    @CsvSource({"0, 3, 3", "2, 3, 1", "3, 3, 0", "4, 3, 0"})
    void stillNeeded(int approvals, int required, int expected) {
        assertThat(new Tally(approvals, 0, required, 4).stillNeeded()).isEqualTo(expected);
    }
}
