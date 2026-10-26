package com.meridian.fit;

import com.meridian.fit.FitScorer.MemberFit;
import com.meridian.profile.ProfileModels.Provenance;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** What the Group fit screen shows. Every number is computed by code ({@link GroupFitCalculator}). */
public final class GroupFitModels {

    private GroupFitModels() {}

    public enum Status { AGREE, TRADE_OFF, CONFLICT, PLAN_AROUND, UNKNOWN }

    /**
     * One member's value for one attribute. {@code hidden} cells belong to someone else's private budget: the value is
     * withheld and only the row's shared range is shown.
     */
    public record Cell(UUID userId, String value, String detail, Integer level, boolean hidden) {

        static Cell hidden(UUID userId) {
            return new Cell(userId, "Private", "Only the shared range is shown", null, true);
        }

        static Cell missing(UUID userId) {
            return new Cell(userId, "—", "No preferences yet", null, false);
        }
    }

    /**
     * One attribute across the group. {@code facts} are the code-computed statements A3 may rephrase; they never
     * contain a private value.
     */
    public record Row(String key, String label, Status status, List<Cell> cells, String summary, String suggestion,
            List<String> facts) {}

    /** Who has done their part so far (spec §5.4 contribution indicator). */
    public record Contribution(UUID userId, String displayName, boolean organizer, boolean hasProfile,
            boolean tripBudgetSet, int itemsAdded, int reactions, int itemsToReact, List<String> missing) {}

    public record SharedBudget(String currency, BigDecimal min, BigDecimal max, int membersWithBudget) {}

    /** A shortlist item's group-fit score and who it works for. {@code score} is null when nobody can be scored. */
    public record ItemFit(UUID itemId, String title, Integer score, int satisfied, int missed, int unknown,
            List<MemberFit> members, String summary) {}

    /** Plain-language trade-off text: from A3, or the code template when AI is off or failed. */
    public record Tradeoff(String key, String headline, String explanation, String suggestion) {}

    public record TradeoffSummary(List<Tradeoff> items, Provenance provenance, String inputKey) {}

    public record Counts(int agree, int tradeOff, int conflict, int planAround, int unknown) {}

    public record GroupFit(
            List<Contribution> contributions,
            List<Row> rows,
            Counts counts,
            SharedBudget sharedBudget,
            List<ItemFit> items,
            TradeoffSummary tradeoffs,
            boolean aiAvailable) {}
}
