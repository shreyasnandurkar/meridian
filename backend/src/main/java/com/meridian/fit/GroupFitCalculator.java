package com.meridian.fit;

import com.meridian.fit.FitScorer.FitMember;
import com.meridian.fit.FitScorer.MemberFit;
import com.meridian.fit.FitScorer.Verdict;
import com.meridian.fit.GroupFitModels.Cell;
import com.meridian.fit.GroupFitModels.Counts;
import com.meridian.fit.GroupFitModels.Row;
import com.meridian.fit.GroupFitModels.SharedBudget;
import com.meridian.fit.GroupFitModels.Status;
import com.meridian.profile.ProfileModels.TravelProfile;
import com.meridian.profile.Vocabulary;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The member × attribute matrix (spec §5.4, A3's numbers). Pure functions over profiles: agreements, trade-offs,
 * conflicts and constraints to plan around, each with a template summary and a suggested compromise.
 *
 * <p>Privacy: budget levels and amounts belong to their owner. Other members' budget cells are hidden unless the owner
 * chose to share their trip budget, and the group only sees ranges and anonymous counts.
 */
public final class GroupFitCalculator {

    /** Spreads on 0–100 sliders: up to this is agreement, up to {@link #TRADE_OFF_SPREAD} a trade-off. */
    static final int AGREE_SPREAD = 25;
    static final int TRADE_OFF_SPREAD = 50;
    /** An interest weight at or above this counts as "likes". */
    static final double LIKES = 0.6;
    static final double LOVES = 0.75;

    /** A member as the matrix sees them: their profile plus this trip's private budget override. */
    public record GroupMember(FitMember fit, BigDecimal budgetMin, BigDecimal budgetMax, boolean budgetVisible) {

        UUID id() {
            return fit.userId();
        }

        String name() {
            return fit.displayName();
        }

        TravelProfile profile() {
            return fit.profile();
        }

        boolean hasBudget() {
            return budgetMin != null || budgetMax != null;
        }
    }

    private static final List<String> PACE_ORDER = List.of("slow", "balanced", "packed");
    private static final List<String> BUDGET_ORDER = List.of("low", "mid", "high");

    private GroupFitCalculator() {}

    public static List<Row> rows(List<GroupMember> members, UUID viewerId, String currency) {
        List<GroupMember> profiled = members.stream().filter(m -> m.profile() != null).toList();
        List<Row> rows = new ArrayList<>();
        rows.add(pace(members, profiled));
        rows.add(interests(members, profiled));
        rows.add(budgetLevel("budget.stay", "Stay budget", "stays", members, profiled, viewerId, b -> b.stay()));
        rows.add(budgetLevel("budget.food", "Food budget", "food", members, profiled, viewerId, b -> b.food()));
        rows.add(budgetLevel("budget.activities", "Activities budget", "activities", members, profiled, viewerId,
                b -> b.activities()));
        rows.add(tripBudget(members, viewerId, currency));
        rows.add(slider("crowds", "Crowds", members, profiled, TravelProfile::crowdTolerance,
                v -> v < 35 ? "Avoids crowds" : v > 65 ? "Enjoys a buzz" : "Doesn't mind",
                "crowd tolerance (0 = avoids crowds, 100 = enjoys a buzz)",
                "Visit the famous spots early in the morning and keep quieter places for the busy hours."));
        rows.add(slider("gems", "Hidden gems ↔ classics", members, profiled, TravelProfile::hiddenGems,
                v -> v < 35 ? "Classics" : v > 65 ? "Hidden gems" : "A mix",
                "hidden-gems preference (0 = classics, 100 = hidden gems)",
                "Pair one well-known classic each day with lesser-known places nearby."));
        rows.add(mustHavesVsDealbreakers(members, profiled));
        rows.add(needs("diet", "Dietary needs", members, profiled, TravelProfile::dietary, Vocabulary.DIETARY,
                "Choose food stops that cover"));
        rows.add(needs("access", "Access needs", members, profiled, TravelProfile::accessNeeds,
                Vocabulary.ACCESS_NEEDS, "Check places and routes for"));
        return rows;
    }

    public static Counts counts(List<Row> rows) {
        Map<Status, Long> by = rows.stream().collect(Collectors.groupingBy(Row::status, Collectors.counting()));
        return new Counts(by.getOrDefault(Status.AGREE, 0L).intValue(), by.getOrDefault(Status.TRADE_OFF, 0L).intValue(),
                by.getOrDefault(Status.CONFLICT, 0L).intValue(), by.getOrDefault(Status.PLAN_AROUND, 0L).intValue(),
                by.getOrDefault(Status.UNKNOWN, 0L).intValue());
    }

    // ---- pace -------------------------------------------------------------------------------------------------

    static Row pace(List<GroupMember> members, List<GroupMember> profiled) {
        List<Cell> cells = members.stream().map(m -> m.profile() == null ? Cell.missing(m.id())
                : new Cell(m.id(), paceLabel(m.profile().pace()), null, PACE_ORDER.indexOf(m.profile().pace()), false))
                .toList();
        if (profiled.size() < 2) return notEnough("pace", "Pace", cells);
        Map<String, List<String>> byPace = groupNames(profiled, m -> m.profile().pace(), PACE_ORDER);
        List<String> facts = byPace.entrySet().stream()
                .map(e -> "pace " + e.getKey() + ": " + String.join(", ", e.getValue())).toList();
        if (byPace.size() == 1) {
            String pace = byPace.keySet().iterator().next();
            return new Row("pace", "Pace", Status.AGREE, cells, "Everyone wants a " + pace + " pace.", null, facts);
        }
        boolean extremes = byPace.containsKey("slow") && byPace.containsKey("packed");
        String summary = byPace.entrySet().stream()
                .map(e -> count(e.getValue().size()) + " " + (e.getValue().size() == 1 ? "wants" : "want") + " "
                        + e.getKey() + " days")
                .collect(Collectors.joining(", "));
        String suggestion = extremes
                ? "Plan one anchor activity a day, with optional extras for those who want more."
                : byPace.containsKey("slow")
                        ? "Keep days light and leave the afternoons free."
                        : "Keep a balanced core and add optional early or late extras.";
        return new Row("pace", "Pace", extremes ? Status.CONFLICT : Status.TRADE_OFF, cells, capitalize(summary) + ".",
                suggestion, facts);
    }

    // ---- interests --------------------------------------------------------------------------------------------

    static Row interests(List<GroupMember> members, List<GroupMember> profiled) {
        Map<String, String> labels = Vocabulary.interestLabels();
        List<Cell> cells = members.stream().map(m -> {
            if (m.profile() == null) return Cell.missing(m.id());
            List<String> top = topInterests(m.profile(), 3).stream().map(labels::get).toList();
            return new Cell(m.id(), top.isEmpty() ? "Open to anything" : String.join(", ", top), null, null, false);
        }).toList();
        if (profiled.size() < 2) return notEnough("interests", "Interests", cells);

        List<String> shared = new ArrayList<>();
        Map<String, List<String>> some = new LinkedHashMap<>();
        for (Vocabulary.Interest interest : Vocabulary.INTERESTS) {
            List<String> fans = profiled.stream()
                    .filter(m -> m.profile().interests().getOrDefault(interest.tag(), 0.0) >= LIKES)
                    .map(GroupMember::name).toList();
            if (fans.size() == profiled.size()) shared.add(interest.label());
            else if (!fans.isEmpty()) some.put(interest.label(), fans);
        }
        List<String> facts = new ArrayList<>();
        if (!shared.isEmpty()) facts.add("liked by everyone: " + String.join(", ", shared));
        some.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, List<String>> e) -> -e.getValue().size()))
                .limit(5)
                .forEach(e -> facts.add("liked only by " + String.join(", ", e.getValue()) + ": " + e.getKey()));
        if (shared.size() >= 2) {
            return new Row("interests", "Interests", Status.AGREE, cells,
                    "Everyone likes " + naturalJoin(lower(shared.subList(0, Math.min(3, shared.size())))) + ".", null,
                    facts);
        }
        String summary = shared.isEmpty()
                ? "No interest is shared by everyone."
                : "Only " + shared.getFirst().toLowerCase(Locale.ROOT) + " is shared by everyone.";
        String niche = some.entrySet().stream().filter(e -> e.getValue().size() == 1).findFirst()
                .map(e -> " " + e.getKey() + " is " + e.getValue().getFirst() + "'s alone.").orElse("");
        return new Row("interests", "Interests", Status.TRADE_OFF, cells, summary + niche,
                "Give each person one stop a day that is theirs, and build the rest around what overlaps.", facts);
    }

    static List<String> topInterests(TravelProfile p, int n) {
        return p.interests().entrySet().stream()
                .filter(e -> e.getValue() >= LIKES)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(n).map(Map.Entry::getKey).toList();
    }

    // ---- budgets ----------------------------------------------------------------------------------------------

    /** Budget levels are private: own and shared cells are shown, others hidden; facts are anonymous counts. */
    static Row budgetLevel(String key, String label, String what, List<GroupMember> members,
            List<GroupMember> profiled, UUID viewerId, Function<com.meridian.profile.ProfileModels.Budget, String> pick) {
        List<Cell> cells = members.stream().map(m -> {
            if (m.profile() == null) return Cell.missing(m.id());
            if (!m.id().equals(viewerId) && !m.budgetVisible()) return Cell.hidden(m.id());
            String level = pick.apply(m.profile().budget());
            return new Cell(m.id(), Vocabulary.label(Vocabulary.BUDGET_LEVELS, level), null,
                    BUDGET_ORDER.indexOf(level), false);
        }).toList();
        if (profiled.size() < 2) return notEnough(key, label, cells);
        List<Integer> levels = profiled.stream().map(m -> BUDGET_ORDER.indexOf(pick.apply(m.profile().budget())))
                .sorted().toList();
        int lo = levels.getFirst();
        int hi = levels.getLast();
        String loLabel = levelLabel(lo);
        Map<String, Long> tally = levels.stream().collect(Collectors.groupingBy(GroupFitCalculator::levelLabel,
                LinkedHashMap::new, Collectors.counting()));
        List<String> facts = List.of(what + " budget levels (anonymous): " + tally.entrySet().stream()
                .map(e -> e.getValue() + " × " + e.getKey()).collect(Collectors.joining(", ")));
        if (lo == hi) {
            return new Row(key, label, Status.AGREE, cells, "Everyone is comfortable with " + loLabel.toLowerCase(
                    Locale.ROOT) + " " + what + ".", null, facts);
        }
        String summary = "Levels range from " + loLabel + " to " + levelLabel(hi) + "; " + loLabel
                + " works for everyone.";
        String suggestion = hi - lo == 2
                ? "Book " + loLabel.toLowerCase(Locale.ROOT) + " " + what
                        + " together and let anyone upgrade on their own."
                : "Aim for the " + loLabel.toLowerCase(Locale.ROOT) + " end with one or two treats.";
        return new Row(key, label, hi - lo == 2 ? Status.CONFLICT : Status.TRADE_OFF, cells, summary, suggestion,
                facts);
    }

    /**
     * The shared range of everyone's trip budget: from the highest minimum to the lowest maximum. Individual amounts
     * stay private unless shared.
     */
    public static SharedBudget sharedBudget(List<GroupMember> members, String currency) {
        List<GroupMember> set = members.stream().filter(GroupMember::hasBudget).toList();
        BigDecimal min = set.stream().map(GroupMember::budgetMin).filter(Objects::nonNull).max(Comparator.naturalOrder())
                .orElse(null);
        BigDecimal max = set.stream().map(GroupMember::budgetMax).filter(Objects::nonNull).min(Comparator.naturalOrder())
                .orElse(null);
        return new SharedBudget(currency, min, max, set.size());
    }

    static Row tripBudget(List<GroupMember> members, UUID viewerId, String currency) {
        List<Cell> cells = members.stream().map(m -> {
            if (!m.hasBudget()) return new Cell(m.id(), "Not set", null, null, false);
            if (!m.id().equals(viewerId) && !m.budgetVisible()) return Cell.hidden(m.id());
            return new Cell(m.id(), range(m.budgetMin(), m.budgetMax(), currency),
                    m.id().equals(viewerId) && !m.budgetVisible() ? "Only you can see this" : "Shared with the group",
                    null, false);
        }).toList();
        SharedBudget shared = sharedBudget(members, currency);
        String label = "Trip budget per person";
        if (shared.membersWithBudget() < 2) {
            return new Row("budget.trip", label, Status.UNKNOWN, cells,
                    shared.membersWithBudget() + " of " + members.size() + " set a trip budget so far.",
                    "Ask everyone to add a private budget range on the Overview tab.", List.of());
        }
        boolean overlaps = shared.min() == null || shared.max() == null || shared.min().compareTo(shared.max()) <= 0;
        String setBy = shared.membersWithBudget() + " of " + members.size() + " members set a budget";
        if (overlaps) {
            String r = range(shared.min(), shared.max(), currency);
            return new Row("budget.trip", label, Status.AGREE, cells,
                    "Everyone's private budget fits " + r + ".", null,
                    List.of(setBy, "shared range everyone fits: " + r));
        }
        return new Row("budget.trip", label, Status.CONFLICT, cells,
                "The private budgets don't overlap: no single amount suits everyone.",
                "Agree a shared core budget and let people upgrade stays or meals individually.",
                List.of(setBy, "the budget ranges do not overlap"));
    }

    // ---- sliders ----------------------------------------------------------------------------------------------

    static Row slider(String key, String label, List<GroupMember> members, List<GroupMember> profiled,
            Function<TravelProfile, Integer> pick, Function<Integer, String> describe, String what,
            String suggestion) {
        List<Cell> cells = members.stream().map(m -> {
            if (m.profile() == null) return Cell.missing(m.id());
            int v = pick.apply(m.profile());
            return new Cell(m.id(), describe.apply(v), v + "/100", v, false);
        }).toList();
        if (profiled.size() < 2) return notEnough(key, label, cells);
        GroupMember low = profiled.stream().min(Comparator.comparingInt(m -> pick.apply(m.profile()))).orElseThrow();
        GroupMember high = profiled.stream().max(Comparator.comparingInt(m -> pick.apply(m.profile()))).orElseThrow();
        int lo = pick.apply(low.profile());
        int hi = pick.apply(high.profile());
        int spread = hi - lo;
        // One line per person, so a small model can't swap who is where or read the gap as an average.
        List<String> facts = new ArrayList<>(List.of("scale: " + what));
        profiled.stream().sorted(Comparator.comparingInt(m -> pick.apply(m.profile()))).forEach(m -> {
            int v = pick.apply(m.profile());
            facts.add(m.name() + " is at " + v + ": " + describe.apply(v).toLowerCase(Locale.ROOT));
        });
        facts.add("gap between lowest and highest: " + spread + " points (a difference, not an average)");
        if (spread <= AGREE_SPREAD) {
            return new Row(key, label, Status.AGREE, cells,
                    "Close together: everyone is within " + spread + " points (" + describe.apply((lo + hi) / 2)
                            .toLowerCase(Locale.ROOT) + ").", null, facts);
        }
        return new Row(key, label, spread <= TRADE_OFF_SPREAD ? Status.TRADE_OFF : Status.CONFLICT, cells,
                low.name() + " is at " + lo + " and " + high.name() + " at " + hi + ", " + spread + " points apart.",
                suggestion, facts);
    }

    // ---- must-haves vs dealbreakers ---------------------------------------------------------------------------

    record Clash(String fan, String wants, String objector, String dealbreaker) {}

    /** One member's must-have (or strong interest) against another member's dealbreaker. */
    static List<Clash> clashes(List<GroupMember> profiled) {
        Map<String, String> labels = Vocabulary.interestLabels();
        List<Clash> out = new ArrayList<>();
        for (GroupMember objector : profiled) {
            for (String dealbreaker : objector.profile().dealbreakers()) {
                Set<String> blocked = new LinkedHashSet<>(Vocabulary.interestsMentioned(dealbreaker));
                if (blocked.isEmpty()) continue;
                for (GroupMember fan : profiled) {
                    if (fan == objector) continue;
                    String wants = fan.fit().mustHaves().stream()
                            .filter(mh -> Vocabulary.interestsMentioned(mh).stream().anyMatch(blocked::contains))
                            .findFirst()
                            .orElseGet(() -> blocked.stream()
                                    .filter(t -> fan.profile().interests().getOrDefault(t, 0.0) >= LOVES)
                                    .findFirst().map(t -> labels.get(t).toLowerCase(Locale.ROOT)).orElse(null));
                    if (wants != null) out.add(new Clash(fan.name(), wants, objector.name(), dealbreaker));
                }
            }
        }
        return out;
    }

    static Row mustHavesVsDealbreakers(List<GroupMember> members, List<GroupMember> profiled) {
        String label = "Must-haves vs dealbreakers";
        List<Cell> cells = members.stream().map(m -> {
            if (m.profile() == null) return Cell.missing(m.id());
            List<String> must = m.fit().mustHaves();
            List<String> never = m.profile().dealbreakers();
            String value = must.isEmpty() && never.isEmpty() ? "None stated"
                    : (must.isEmpty() ? "" : "Must: " + String.join(", ", must))
                            + (must.isEmpty() || never.isEmpty() ? "" : " · ")
                            + (never.isEmpty() ? "" : "Never: " + String.join(", ", never));
            return new Cell(m.id(), value, null, null, false);
        }).toList();
        if (profiled.size() < 2) return notEnough("musts", label, cells);
        List<Clash> clashes = clashes(profiled);
        if (clashes.isEmpty()) {
            return new Row("musts", label, Status.AGREE, cells,
                    "No one's must-have runs into someone else's dealbreaker.", null, List.of());
        }
        List<String> facts = clashes.stream().map(c -> c.fan() + " wants “" + c.wants() + "”; "
                + c.objector() + "'s dealbreaker is “" + c.dealbreaker() + "”").distinct().toList();
        Clash first = clashes.getFirst();
        String summary = first.fan() + " wants " + first.wants() + ", but " + first.objector()
                + " won't do “" + first.dealbreaker() + "”"
                + (clashes.size() > 1 ? " (+" + (clashes.size() - 1) + " more)." : ".");
        return new Row("musts", label, Status.CONFLICT, cells, summary,
                "Schedule it as an optional evening or split-up slot, so nobody has to join something they refuse.",
                facts);
    }

    // ---- needs ------------------------------------------------------------------------------------------------

    static Row needs(String key, String label, List<GroupMember> members, List<GroupMember> profiled,
            Function<TravelProfile, List<String>> pick, List<Vocabulary.Option> vocabulary, String verb) {
        List<Cell> cells = members.stream().map(m -> {
            if (m.profile() == null) return Cell.missing(m.id());
            List<String> values = pick.apply(m.profile());
            return new Cell(m.id(), values.isEmpty() ? "None"
                    : values.stream().map(v -> Vocabulary.label(vocabulary, v)).collect(Collectors.joining(", ")),
                    null, values.size(), false);
        }).toList();
        if (profiled.isEmpty()) return notEnough(key, label, cells);
        Map<String, List<String>> who = new LinkedHashMap<>();
        for (Vocabulary.Option option : vocabulary) {
            List<String> names = profiled.stream().filter(m -> pick.apply(m.profile()).contains(option.id()))
                    .map(GroupMember::name).toList();
            if (!names.isEmpty()) who.put(option.label(), names);
        }
        if (who.isEmpty()) {
            return new Row(key, label, Status.AGREE, cells, "Nothing to plan around.", null, List.of());
        }
        List<String> facts = who.entrySet().stream()
                .map(e -> e.getKey() + ": " + String.join(", ", e.getValue())).toList();
        String list = naturalJoin(who.entrySet().stream()
                .map(e -> e.getKey().toLowerCase(Locale.ROOT) + " (" + String.join(", ", e.getValue()) + ")")
                .toList());
        return new Row(key, label, Status.PLAN_AROUND, cells, capitalize(list) + ".", verb + " " + naturalJoin(
                who.keySet().stream().map(s -> s.toLowerCase(Locale.ROOT)).toList()) + ".", facts);
    }

    // ---- per-item group score ---------------------------------------------------------------------------------

    /**
     * Group score for an item: mostly the average member fit, pulled down by the least happy member so one person's
     * dealbreaker is never averaged away. Every member counts equally.
     */
    public static Integer groupScore(List<MemberFit> fits) {
        List<Integer> scores = fits.stream().map(MemberFit::score).filter(Objects::nonNull).toList();
        if (scores.isEmpty()) return null;
        double mean = scores.stream().mapToInt(Integer::intValue).average().orElseThrow();
        int min = scores.stream().mapToInt(Integer::intValue).min().orElseThrow();
        return (int) Math.round(0.7 * mean + 0.3 * min);
    }

    public static String itemSummary(List<MemberFit> fits) {
        List<String> yes = fits.stream().filter(f -> f.verdict() == Verdict.SATISFIES).map(MemberFit::displayName)
                .toList();
        List<MemberFit> no = fits.stream().filter(f -> f.verdict() == Verdict.MISSES).toList();
        long unknown = fits.stream().filter(f -> f.verdict() == Verdict.UNKNOWN).count();
        List<String> parts = new ArrayList<>();
        if (!yes.isEmpty()) {
            parts.add(yes.size() == fits.size() ? "Works for everyone" : "Works for " + naturalJoin(yes));
        }
        if (!no.isEmpty()) {
            parts.add("not for " + naturalJoin(no.stream().map(f -> f.displayName() + f.reasons().stream()
                    .filter(r -> r.effect() == FitScorer.Effect.BLOCKER || r.effect() == FitScorer.Effect.MINUS)
                    .findFirst().map(r -> " (" + lowerFirst(r.text()) + ")").orElse("")).toList()));
        }
        if (parts.isEmpty()) parts.add("Nobody is strongly for or against it");
        if (unknown > 0) parts.add(unknown + " without preferences yet");
        return capitalize(String.join("; ", parts)) + ".";
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private static Row notEnough(String key, String label, List<Cell> cells) {
        return new Row(key, label, Status.UNKNOWN, cells, "Needs at least two members' preferences.", null,
                List.of());
    }

    private static Map<String, List<String>> groupNames(List<GroupMember> members,
            Function<GroupMember, String> value, List<String> order) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String v : order) {
            List<String> names = members.stream().filter(m -> v.equals(value.apply(m))).map(GroupMember::name).toList();
            if (!names.isEmpty()) out.put(v, names);
        }
        return out;
    }

    private static String paceLabel(String pace) {
        return switch (pace) {
            case "slow" -> "Slow";
            case "packed" -> "Packed";
            default -> "Balanced";
        };
    }

    private static String levelLabel(int index) {
        return Vocabulary.label(Vocabulary.BUDGET_LEVELS, BUDGET_ORDER.get(index));
    }

    static String range(BigDecimal min, BigDecimal max, String currency) {
        NumberFormat f = NumberFormat.getIntegerInstance(Locale.ENGLISH);
        if (min != null && max != null) return currency + " " + f.format(min) + "–" + f.format(max);
        if (min != null) return currency + " " + f.format(min) + " or more";
        if (max != null) return "up to " + currency + " " + f.format(max);
        return "any amount";
    }

    private static String count(int n) {
        return String.valueOf(n);
    }

    private static List<String> lower(List<String> items) {
        return items.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }

    static String naturalJoin(List<String> items) {
        if (items.isEmpty()) return "";
        if (items.size() == 1) return items.getFirst();
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.getLast();
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String lowerFirst(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
