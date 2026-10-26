package com.meridian.trip;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QuorumTest {

    @ParameterizedTest(name = "{0} members, min {1} -> {2}")
    @CsvSource(nullValues = "null", value = {
        "1, null, 1",
        "2, null, 2",
        "3, null, 2",
        "4, null, 3",
        "5, null, 3",
        "4, 2, 2",
        "4, 9, 4",   // cannot require more approvals than members
        "4, 0, 1",
        "0, null, 1",
    })
    void requiredApprovals(int members, Integer min, int expected) {
        assertThat(Quorum.requiredApprovals(members, min)).isEqualTo(expected);
    }
}
