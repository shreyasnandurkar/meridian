package com.meridian.recommend;

import com.meridian.fit.FitScorer;
import com.meridian.fit.FitScorer.FitMember;
import com.meridian.fit.FitScorer.MemberFit;
import com.meridian.fit.FitScorer.Verdict;
import com.meridian.fit.GroupFitCalculator;
import com.meridian.fit.PlaceSignals;
import com.meridian.fit.TouristTrapScorer;
import com.meridian.geo.GeoMath;
import com.meridian.place.PlaceDtos.PlaceView;
import com.meridian.profile.Vocabulary;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic part of A2: scores every candidate for the group. The score is the group-fit score (tag match per
 * member, with the hidden-gems slider applied as the anti-tourist-trap weight), and "avoid crowds" drops chains and
 * pushes famous places down. No commercial signal exists anywhere in the input.
 */
public final class CandidateScorer {

    /** How many top candidates the model may re-rank (small-model rule: ≤10). */
    public static final int TOP = 10;

    public record Candidate(PlaceView place, PlaceSignals signals, int score, List<String> matchedInterests,
            List<String> fits, List<String> misses, List<String> localSignals, double distanceKm, String rulesWhy) {}

    private CandidateScorer() {}

    /** Default slider position: the group's average hidden-gems setting. */
    public static int groupGems(List<FitMember> members) {
        return (int) Math.round(members.stream().filter(m -> m.profile() != null)
                .mapToInt(m -> m.profile().hiddenGems()).average().orElse(50));
    }

    public static List<Candidate> rank(List<PlaceView> places, Set<UUID> exclude, List<FitMember> members,
            TouristTrapScorer trapScorer, int gems, boolean avoidCrowds, Double originLat, Double originLng) {
        List<Candidate> out = new ArrayList<>();
        for (PlaceView place : places) {
            if (exclude.contains(place.id())) continue;
            PlaceSignals signals = PlaceSignals.of(place);
            if (avoidCrowds && signals.isChain()) continue;
            TouristTrapScorer.Assessment trap = trapScorer.assess(signals);
            List<MemberFit> fits = members.stream()
                    .map(m -> FitScorer.score(m, signals, trap, null, null, gems)).toList();
            Integer group = GroupFitCalculator.groupScore(fits);
            double score = group != null ? group : 50 + 100 * TouristTrapScorer.gemAdjustment(trap, gems);
            if (avoidCrowds) score -= 30 * trap.fame();
            double distance = originLat == null || originLng == null ? 0
                    : GeoMath.distanceKm(originLat, originLng, place.lat(), place.lng());
            List<String> matched = matchedInterests(signals, members);
            List<String> yes = fits.stream().filter(f -> f.verdict() == Verdict.SATISFIES)
                    .map(MemberFit::displayName).toList();
            List<String> no = fits.stream().filter(f -> f.verdict() == Verdict.MISSES)
                    .map(f -> f.displayName() + f.reasons().stream()
                            .filter(r -> r.effect() == FitScorer.Effect.BLOCKER || r.effect() == FitScorer.Effect.MINUS)
                            .findFirst().map(r -> " (" + lowerFirst(r.text()) + ")").orElse(""))
                    .toList();
            int finalScore = (int) Math.round(Math.clamp(score, 0, 100));
            out.add(new Candidate(place, signals, finalScore, matched, yes, no, trap.signals(), round1(distance),
                    rulesWhy(matched, yes, no, trap.signals(), members)));
        }
        out.sort(Comparator.comparingInt(Candidate::score).reversed()
                .thenComparingDouble(Candidate::distanceKm)
                .thenComparing(c -> c.place().name()));
        return out;
    }

    /** Interests this place offers (strength ≥ 0.5) that at least one member likes, strongest first. */
    static List<String> matchedInterests(PlaceSignals signals, List<FitMember> members) {
        Map<String, String> labels = Vocabulary.interestLabels();
        return signals.interests().entrySet().stream()
                .filter(e -> e.getValue() >= 0.5)
                .filter(e -> members.stream().anyMatch(m -> m.profile() != null
                        && m.profile().interests().getOrDefault(e.getKey(), 0.0) >= 0.6))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(3)
                .map(e -> labels.get(e.getKey()).toLowerCase(Locale.ROOT))
                .toList();
    }

    /** Template "why this fits", used when AI is off or fails, and as the AI's grounding. */
    static String rulesWhy(List<String> matched, List<String> fits, List<String> misses, List<String> signals,
            List<FitMember> members) {
        long profiled = members.stream().filter(m -> m.profile() != null).count();
        StringBuilder why = new StringBuilder();
        String who = fits.isEmpty() ? "" : fits.size() == profiled && profiled > 1 ? "everyone" : join(fits);
        if (!fits.isEmpty() && !matched.isEmpty()) {
            why.append("Offers ").append(join(matched)).append(", which suits ").append(who);
        } else if (!fits.isEmpty()) {
            why.append("Suits ").append(who);
        } else if (!matched.isEmpty()) {
            why.append("Offers ").append(join(matched));
        } else {
            why.append("Nearby option with no strong match yet");
        }
        why.append('.');
        if (!signals.isEmpty()) why.append(' ').append(capitalize(signals.getFirst())).append('.');
        if (!misses.isEmpty()) why.append(" Less suited to ").append(join(misses)).append('.');
        return why.toString();
    }

    private static String join(List<String> items) {
        if (items.size() == 1) return items.getFirst();
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.getLast();
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String lowerFirst(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
