package com.meridian.fit;

import com.meridian.place.PlaceCategory;
import com.meridian.profile.ProfileModels.TravelProfile;
import com.meridian.profile.Vocabulary;
import com.meridian.shortlist.Reaction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Deterministic member × place fit (spec §5.4). Every number here comes from code; the model never scores. A member's
 * own reaction overrides the estimate, because what they said beats what we guessed.
 */
public final class FitScorer {

    /** Weight of an interest a member never rated: mildly open to it, not against it. */
    static final double UNRATED_WEIGHT = 0.25;
    static final int SATISFIES_AT = 65;
    static final int MISSES_BELOW = 40;
    /** A member's dealbreaker caps the score here, whatever else matches. */
    static final int BLOCKER_CAP = 15;

    public enum Verdict { SATISFIES, NEUTRAL, MISSES, UNKNOWN }

    public enum Effect { PLUS, MINUS, BLOCKER, NOTE }

    public record Reason(Effect effect, String text) {}

    /** The member side of scoring. {@code profile} is null until they take the quiz. */
    public record FitMember(UUID userId, String displayName, TravelProfile profile, List<String> tripMustHaves) {

        public FitMember {
            tripMustHaves = tripMustHaves == null ? List.of() : List.copyOf(tripMustHaves);
        }

        public List<String> mustHaves() {
            if (profile == null) return tripMustHaves;
            List<String> all = new ArrayList<>(profile.mustHaves());
            tripMustHaves.stream().filter(m -> !all.contains(m)).forEach(all::add);
            return all;
        }
    }

    public record MemberFit(UUID userId, String displayName, Integer score, Verdict verdict, List<Reason> reasons,
            Reaction.Value reaction) {}

    private static final Set<String> VEG_DIETS = Set.of("vegetarian", "vegan", "jain");
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    private FitScorer() {}

    /**
     * @param gemsOverride when set, used instead of the member's own hidden-gems setting (the recommendation slider)
     */
    public static MemberFit score(FitMember m, PlaceSignals place, TouristTrapScorer.Assessment trap,
            Reaction.Value reaction, String reactionReason, Integer gemsOverride) {
        if (m.profile() == null) {
            List<Reason> reasons = new ArrayList<>();
            reasons.add(new Reason(Effect.NOTE, "Hasn't filled in preferences yet"));
            Integer score = null;
            Verdict verdict = Verdict.UNKNOWN;
            if (reaction != null) {
                score = switch (reaction) {
                    case LOVE -> 90;
                    case OK -> 60;
                    case NO_WAY -> 10;
                };
                verdict = verdict(score);
                reasons.add(reactionReason(reaction, reactionReason));
            }
            return new MemberFit(m.userId(), m.displayName(), score, verdict, reasons, reaction);
        }
        TravelProfile p = m.profile();
        List<Reason> reasons = new ArrayList<>();
        boolean blocked = false;

        double interest = interestMatch(p, place, reasons);
        double adjust = 0;

        int gems = gemsOverride != null ? gemsOverride : p.hiddenGems();
        double gem = TouristTrapScorer.gemAdjustment(trap, gems);
        adjust += gem;
        if (gems >= 60 && gem >= 0.05) reasons.add(new Reason(Effect.PLUS, "Off the usual tourist trail"));
        else if (gems >= 60 && gem <= -0.08) reasons.add(new Reason(Effect.MINUS, "Too touristy for a hidden-gems fan"));
        else if (gems <= 40 && gem >= 0.08) reasons.add(new Reason(Effect.PLUS, "A well-known classic"));

        if (p.crowdTolerance() < 40 && trap.fame() >= 0.5) {
            adjust -= (40 - p.crowdTolerance()) / 40.0 * 0.2;
            reasons.add(new Reason(Effect.MINUS, "Likely crowded; prefers quiet places"));
        }

        for (String dealbreaker : p.dealbreakers()) {
            if (hits(dealbreaker, place)) {
                blocked = true;
                reasons.add(new Reason(Effect.BLOCKER, "Clashes with their dealbreaker “" + dealbreaker + "”"));
            }
        }
        for (String mustHave : m.mustHaves()) {
            if (hits(mustHave, place)) {
                adjust += 0.1;
                reasons.add(new Reason(Effect.PLUS, "Covers their must-have “" + mustHave + "”"));
            }
        }

        adjust += dietary(p.dietary(), place, reasons);
        double access = access(p.accessNeeds(), place, reasons);
        if (access <= -0.3) blocked = true;
        adjust += access;
        adjust += budget(p, place, reasons);

        int score = (int) Math.round(Math.clamp((0.15 + 0.75 * interest + adjust) * 100, 0, 100));
        if (blocked) score = Math.min(score, BLOCKER_CAP);
        if (reaction != null) {
            score = switch (reaction) {
                case LOVE -> Math.max(score, 90);
                case OK -> Math.clamp(score, 50, 80);
                case NO_WAY -> Math.min(score, 10);
            };
            reasons.addFirst(reactionReason(reaction, reactionReason));
        }
        return new MemberFit(m.userId(), m.displayName(), score, verdict(score), List.copyOf(reasons), reaction);
    }

    static Verdict verdict(int score) {
        return score >= SATISFIES_AT ? Verdict.SATISFIES : score < MISSES_BELOW ? Verdict.MISSES : Verdict.NEUTRAL;
    }

    /**
     * Strength-weighted average of the member's interest weights over what the place offers (topped up to a total
     * strength of 1 with unrated weight), in [0, 1]. Unknown places score a neutral 0.5.
     */
    static double interestMatch(TravelProfile p, PlaceSignals place, List<Reason> reasons) {
        if (place.interests().isEmpty()) return 0.5;
        Map<String, String> labels = Vocabulary.interestLabels();
        double sum = 0;
        double total = 0;
        for (var e : place.interests().entrySet()) {
            double w = p.interests().getOrDefault(e.getKey(), UNRATED_WEIGHT);
            sum += e.getValue() * w;
            total += e.getValue();
        }
        // A place that only weakly offers something (a pizzeria's 0.2 local food) mustn't match as fully as one that
        // clearly does: the strength it lacks counts as unrated.
        if (total < 1) {
            sum += (1 - total) * UNRATED_WEIGHT;
            total = 1;
        }
        // the strongest liked and disliked aspects explain the number
        place.interests().entrySet().stream()
                .filter(e -> e.getValue() >= 0.5 && p.interests().getOrDefault(e.getKey(), UNRATED_WEIGHT) >= 0.6)
                .max(Comparator.comparingDouble(e -> e.getValue() * p.interests().get(e.getKey())))
                .ifPresent(e -> reasons.add(new Reason(Effect.PLUS,
                        "Into " + labels.get(e.getKey()).toLowerCase(Locale.ROOT))));
        place.interests().entrySet().stream()
                .filter(e -> e.getValue() >= 0.7 && p.interests().containsKey(e.getKey())
                        && p.interests().get(e.getKey()) <= 0.15)
                .findFirst()
                .ifPresent(e -> reasons.add(new Reason(Effect.MINUS,
                        "Not into " + labels.get(e.getKey()).toLowerCase(Locale.ROOT))));
        return sum / total;
    }

    /**
     * Whether a free-text must-have or dealbreaker ("clubs", "beach time") applies to a place: by the interests its
     * words point to, or by sharing a meaningful word with the place name.
     */
    static boolean hits(String phrase, PlaceSignals place) {
        for (String tag : Vocabulary.interestsMentioned(phrase)) {
            if (place.strength(tag) >= 0.5) return true;
        }
        Set<String> nameWords = words(place.name());
        return words(phrase).stream().anyMatch(w -> w.length() >= 4 && nameWords.contains(w));
    }

    private static Set<String> words(String s) {
        if (s == null) return Set.of();
        // Names repeat words ("Kabai Kabai"), so no Set.of here.
        return Arrays.stream(NOT_WORD.split(s.toLowerCase(Locale.ROOT)))
                .map(w -> w.endsWith("s") && w.length() > 4 ? w.substring(0, w.length() - 1) : w)
                .collect(Collectors.toSet());
    }

    private static double dietary(List<String> diets, PlaceSignals place, List<Reason> reasons) {
        if (place.category() != PlaceCategory.FOOD || diets.isEmpty()) return 0;
        Map<String, String> tags = place.tags();
        Set<String> cuisines = PlaceInterests.cuisines(tags);
        boolean seafood = cuisines.contains("seafood") || cuisines.contains("fish");
        double adjust = 0;
        if (diets.stream().anyMatch(VEG_DIETS::contains)) {
            String veg = diets.contains("vegan") ? tags.get("diet:vegan") : tags.get("diet:vegetarian");
            if ("yes".equals(veg) || "only".equals(veg)) {
                adjust += 0.05;
                reasons.add(new Reason(Effect.PLUS, "Vegetarian options listed"));
            } else if ("no".equals(veg)) {
                adjust -= 0.3;
                reasons.add(new Reason(Effect.MINUS, "No vegetarian options listed"));
            } else if (seafood) {
                adjust -= 0.1;
                reasons.add(new Reason(Effect.MINUS, "Seafood place; vegetarian options unknown"));
            } else {
                reasons.add(new Reason(Effect.NOTE, "OpenStreetMap doesn't say if it has vegetarian food"));
            }
        }
        if (diets.contains("no_seafood") && seafood) {
            adjust -= 0.15;
            reasons.add(new Reason(Effect.MINUS, "Mostly seafood; they avoid it"));
        }
        if (diets.contains("halal") && "yes".equals(tags.get("diet:halal"))) {
            adjust += 0.05;
            reasons.add(new Reason(Effect.PLUS, "Halal food listed"));
        }
        if (diets.contains("no_alcohol") && place.strength("nightlife") >= 0.8) {
            adjust -= 0.2;
            reasons.add(new Reason(Effect.MINUS, "A bar; they don't drink"));
        }
        return adjust;
    }

    private static double access(List<String> needs, PlaceSignals place, List<Reason> reasons) {
        if (needs.isEmpty()) return 0;
        double adjust = 0;
        String wheelchair = place.tags().get("wheelchair");
        if (needs.contains("wheelchair") || needs.contains("step_free")) {
            if ("yes".equals(wheelchair)) {
                adjust += 0.05;
                reasons.add(new Reason(Effect.PLUS, "Step-free access listed"));
            } else if ("no".equals(wheelchair)) {
                adjust -= 0.3;
                reasons.add(new Reason(Effect.BLOCKER, "Not wheelchair accessible (per OpenStreetMap)"));
            } else {
                reasons.add(new Reason(Effect.NOTE, "Accessibility not recorded; check before going"));
            }
        }
        if (needs.contains("limited_walking") && place.strength("hiking") >= 0.6) {
            adjust -= 0.2;
            reasons.add(new Reason(Effect.MINUS, "Involves a walk or climb"));
        }
        if (needs.contains("quiet_spaces") && place.strength("nightlife") >= 0.8) {
            adjust -= 0.15;
            reasons.add(new Reason(Effect.MINUS, "Loud nightlife spot; needs quiet"));
        }
        if (needs.contains("child_friendly") && place.strength("nightlife") >= 0.8) {
            adjust -= 0.2;
            reasons.add(new Reason(Effect.MINUS, "Not a place for children"));
        }
        return adjust;
    }

    private static double budget(TravelProfile p, PlaceSignals place, List<Reason> reasons) {
        if (place.category() != PlaceCategory.STAYS) return 0;
        String level = p.budget().stay();
        int stars = stars(place.tags().get("stars"));
        boolean modest = Set.of("hostel", "guest_house", "camp_site").contains(place.tags().getOrDefault("tourism", ""));
        if (stars >= 4 && "low".equals(level)) {
            reasons.add(new Reason(Effect.MINUS, stars + "-star stay; above their stay budget"));
            return -0.2;
        }
        if ((modest || (stars > 0 && stars <= 2)) && "low".equals(level)) {
            reasons.add(new Reason(Effect.PLUS, "Fits a budget stay"));
            return 0.05;
        }
        if (stars >= 4 && "high".equals(level)) {
            reasons.add(new Reason(Effect.PLUS, "The comfortable kind of stay they like"));
            return 0.05;
        }
        return 0;
    }

    private static int stars(String v) {
        if (v == null) return 0;
        try {
            return Integer.parseInt(v.replaceAll("[^0-9].*$", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Reason reactionReason(Reaction.Value reaction, String reason) {
        String said = switch (reaction) {
            case LOVE -> "Loves it";
            case OK -> "Is OK with it";
            case NO_WAY -> "Said no way";
        };
        String text = reason == null || reason.isBlank() ? said + " (their reaction)" : said + ": “" + reason + "”";
        return new Reason(reaction == Reaction.Value.NO_WAY ? Effect.BLOCKER
                : reaction == Reaction.Value.LOVE ? Effect.PLUS : Effect.NOTE, text);
    }
}
