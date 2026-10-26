package com.meridian.fit;

import static com.meridian.fit.Profiles.profile;
import static org.assertj.core.api.Assertions.assertThat;

import com.meridian.fit.FitScorer.MemberFit;
import com.meridian.fit.FitScorer.Verdict;
import com.meridian.fit.GroupFitCalculator.GroupMember;
import com.meridian.fit.GroupFitModels.Cell;
import com.meridian.fit.GroupFitModels.Row;
import com.meridian.fit.GroupFitModels.Status;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroupFitCalculatorTest {

    private final GroupMember asha = profile().pace("slow").interests("beaches", 1.0, "local_food", 1.0, "history", 1.0)
            .budget("low", "low", "mid").crowd(20).gems(80).dietary("vegetarian").group("Asha");
    private final GroupMember dev = profile().pace("packed").interests("beaches", 0.75, "local_food", 0.75,
            "nightlife", 1.0).budget("high", "mid", "mid").crowd(90).gems(70).mustHaves("beach parties").group("Dev");
    private final GroupMember meera = profile().pace("balanced").interests("beaches", 0.75, "local_food", 1.0)
            .budget("mid", "mid", "mid").crowd(30).gems(60).dealbreakers("loud clubs").group("Meera");

    private Row row(List<Row> rows, String key) {
        return rows.stream().filter(r -> r.key().equals(key)).findFirst().orElseThrow();
    }

    private List<Row> rows(List<GroupMember> members, UUID viewer) {
        return GroupFitCalculator.rows(members, viewer, "INR");
    }

    @Test
    void paceExtremesAreAConflict() {
        Row pace = row(rows(List.of(asha, dev, meera), asha.id()), "pace");
        assertThat(pace.status()).isEqualTo(Status.CONFLICT);
        assertThat(pace.summary()).isEqualTo("1 wants slow days, 1 wants balanced days, 1 wants packed days.");
        assertThat(pace.suggestion()).isNotBlank();

        Row tradeoff = row(rows(List.of(asha, meera), asha.id()), "pace");
        assertThat(tradeoff.status()).isEqualTo(Status.TRADE_OFF);
        assertThat(row(rows(List.of(meera, meera), asha.id()), "pace").status()).isEqualTo(Status.AGREE);
    }

    @Test
    void sharedInterestsAreAgreement() {
        Row interests = row(rows(List.of(asha, dev, meera), asha.id()), "interests");
        assertThat(interests.status()).isEqualTo(Status.AGREE);
        assertThat(interests.summary()).isEqualTo("Everyone likes local food and beaches.");
        assertThat(interests.facts()).anyMatch(f -> f.equals("liked only by Asha: History & heritage"));
    }

    @Test
    void sliderSpreadsAreClassifiedByThreshold() {
        List<Row> rs = rows(List.of(asha, dev, meera), asha.id());
        Row crowds = row(rs, "crowds");
        assertThat(crowds.status()).isEqualTo(Status.CONFLICT);
        assertThat(crowds.summary()).isEqualTo("Asha is at 20 and Dev at 90, 70 points apart.");
        assertThat(row(rs, "gems").status()).isEqualTo(Status.AGREE);
    }

    @Test
    void mustHaveAgainstDealbreakerIsAConflict() {
        Row musts = row(rows(List.of(asha, dev, meera), asha.id()), "musts");
        assertThat(musts.status()).isEqualTo(Status.CONFLICT);
        assertThat(musts.facts()).contains(
                "Dev wants “beach parties”; Meera's dealbreaker is “loud clubs”");
    }

    @Test
    void dietaryNeedsArePlannedAround() {
        Row diet = row(rows(List.of(asha, dev, meera), asha.id()), "diet");
        assertThat(diet.status()).isEqualTo(Status.PLAN_AROUND);
        assertThat(diet.summary()).isEqualTo("Vegetarian (Asha).");
    }

    @Test
    void budgetLevelsAreHiddenFromOthersAndFactsAreAnonymous() {
        List<Row> rs = rows(List.of(asha, dev, meera), asha.id());
        Row stay = row(rs, "budget.stay");
        assertThat(stay.status()).isEqualTo(Status.CONFLICT);
        assertThat(stay.cells().get(0).value()).isEqualTo("Budget");
        assertThat(stay.cells().get(1).hidden()).isTrue();
        assertThat(stay.cells().get(1).value()).isEqualTo("Private");
        assertThat(String.join(" ", stay.facts())).doesNotContain("Dev").doesNotContain("Meera").doesNotContain("Asha");
        assertThat(stay.summary()).isEqualTo("Levels range from Budget to Splurge; Budget works for everyone.");
    }

    @Test
    void tripBudgetShowsOnlyTheSharedRange() {
        GroupMember a = Profiles.withBudget(asha, 20000, 40000, false);
        GroupMember d = Profiles.withBudget(dev, 30000, 90000, false);
        GroupMember m = Profiles.withBudget(meera, 25000, 50000, true);
        List<Row> rs = rows(List.of(a, d, m), a.id());
        Row trip = row(rs, "budget.trip");
        assertThat(trip.status()).isEqualTo(Status.AGREE);
        assertThat(trip.summary()).isEqualTo("Everyone's private budget fits INR 30,000–40,000.");
        List<Cell> cells = trip.cells();
        assertThat(cells.get(0).value()).isEqualTo("INR 20,000–40,000"); // my own
        assertThat(cells.get(1).hidden()).isTrue(); // Dev did not share
        assertThat(cells.get(2).value()).isEqualTo("INR 25,000–50,000"); // Meera shared
        assertThat(String.join(" ", trip.facts())).doesNotContain("90").doesNotContain("20,000");

        GroupMember rich = Profiles.withBudget(dev, 60000, 90000, false);
        Row apart = row(rows(List.of(a, rich), a.id()), "budget.trip");
        assertThat(apart.status()).isEqualTo(Status.CONFLICT);
        assertThat(String.join(" ", apart.facts())).doesNotContainPattern("\\d{4}");

        var shared = GroupFitCalculator.sharedBudget(List.of(a, d, m), "INR");
        assertThat(shared.min()).isEqualByComparingTo(BigDecimal.valueOf(30000));
        assertThat(shared.max()).isEqualByComparingTo(BigDecimal.valueOf(40000));
    }

    @Test
    void membersWithoutProfilesAreShownButNotCounted() {
        GroupMember newbie = Profiles.noProfile("Kim");
        List<Row> rs = rows(List.of(asha, newbie), asha.id());
        assertThat(row(rs, "pace").status()).isEqualTo(Status.UNKNOWN);
        assertThat(row(rs, "pace").cells().get(1).detail()).isEqualTo("No preferences yet");
    }

    @Test
    void groupScoreIsPulledDownByTheLeastHappyMember() {
        List<MemberFit> fits = List.of(fit("A", 90), fit("B", 90), fit("C", 10));
        // mean 63.3, min 10 -> 0.7*63.3 + 0.3*10 = 47.3
        assertThat(GroupFitCalculator.groupScore(fits)).isEqualTo(47);
        assertThat(GroupFitCalculator.groupScore(List.of(new MemberFit(UUID.randomUUID(), "X", null,
                Verdict.UNKNOWN, List.of(), null)))).isNull();
    }

    @Test
    void itemSummaryNamesWhoItWorksFor() {
        List<MemberFit> fits = List.of(fit("Asha", 80), fit("Dev", 50), new MemberFit(UUID.randomUUID(), "Meera", 10,
                Verdict.MISSES, List.of(new FitScorer.Reason(FitScorer.Effect.BLOCKER, "Clashes with X")), null));
        assertThat(GroupFitCalculator.itemSummary(fits)).isEqualTo("Works for Asha; not for Meera (clashes with X).");
    }

    private static MemberFit fit(String name, int score) {
        return new MemberFit(UUID.randomUUID(), name, score, FitScorer.verdict(score), List.of(), null);
    }
}
