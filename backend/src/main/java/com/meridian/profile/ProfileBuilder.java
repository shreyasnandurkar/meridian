package com.meridian.profile;

import com.meridian.profile.ProfileModels.AiExtraction;
import com.meridian.profile.ProfileModels.AiExtraction.ExtraInterest;
import com.meridian.profile.ProfileModels.Inference;
import com.meridian.profile.ProfileModels.QuizAnswers;
import com.meridian.profile.ProfileModels.TravelProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;

/**
 * Deterministic half of A1. Structured quiz answers map straight onto the profile (no model needed); only the
 * free-text answer is open to interpretation, either by keyword rules or by the model's {@link AiExtraction}.
 */
public final class ProfileBuilder {

    /** Weight given to an interest found by keyword rules in the free text. */
    static final double RULE_WEIGHT = 0.6;

    /** An interest the traveler mentioned is never weighted below a "mild mention" (small models sometimes emit 0). */
    static final double MIN_INFERRED_WEIGHT = 0.3;

    private ProfileBuilder() {}

    public record Built(TravelProfile profile, List<Inference> inferences) {}

    /** Reads the free text with keyword rules — the fallback when AI is off, fails, or there is no text. */
    public static AiExtraction ruleExtraction(QuizAnswers q) {
        List<ExtraInterest> extras = new ArrayList<>();
        String text = q.idealTrip();
        if (!text.isBlank()) {
            for (Vocabulary.Interest interest : Vocabulary.INTERESTS) {
                if (q.interestRatings().containsKey(interest.tag())) continue;
                for (String keyword : interest.keywords()) {
                    Matcher m = Vocabulary.keywordPattern(keyword).matcher(text);
                    if (m.find()) {
                        extras.add(new ExtraInterest(interest.tag(), RULE_WEIGHT, snippet(text, m.start(), m.end())));
                        break;
                    }
                }
            }
        }
        return new AiExtraction(extras, List.of(), List.of(), null);
    }

    /** Combines quiz answers with an extraction. Explicit answers always win over anything inferred. */
    public static Built build(QuizAnswers q, AiExtraction x) {
        Map<String, Double> interests = new TreeMap<>();
        q.interestRatings().forEach((tag, rating) -> interests.put(tag, round(rating / 4.0)));

        List<Inference> inferences = new ArrayList<>();
        String lowerText = q.idealTrip().toLowerCase(Locale.ROOT);
        x.extraInterests().stream()
                .sorted(Comparator.comparingDouble(ExtraInterest::weight).reversed())
                .filter(i -> Vocabulary.INTEREST_TAGS.contains(i.tag()))
                .filter(i -> !interests.containsKey(i.tag()))
                // grounding: the quoted evidence must actually appear in what the traveler wrote
                .filter(i -> i.evidence() != null && lowerText.contains(i.evidence().toLowerCase(Locale.ROOT).strip()))
                .forEach(i -> {
                    double weight = round(Math.max(MIN_INFERRED_WEIGHT, Math.min(1, i.weight())));
                    interests.put(i.tag(), weight);
                    inferences.add(new Inference("interests." + i.tag(), String.valueOf(weight), i.evidence()));
                });

        List<String> mustHaves = mergeLists(q.mustHaves(), x.mustHaves(), "mustHaves", inferences);
        List<String> dealbreakers = mergeLists(q.dealbreakers(), x.dealbreakers(), "dealbreakers", inferences);

        TravelProfile draft = new TravelProfile(interests, q.pace(), q.budget(), q.crowdTolerance(), q.hiddenGems(),
                distinct(q.dietary()), distinct(q.accessNeeds()), mustHaves, dealbreakers, null);
        String summary = x.summary() != null && !x.summary().isBlank() ? x.summary().strip() : templateSummary(draft);
        TravelProfile profile = new TravelProfile(draft.interests(), draft.pace(), draft.budget(),
                draft.crowdTolerance(), draft.hiddenGems(), draft.dietary(), draft.accessNeeds(), draft.mustHaves(),
                draft.dealbreakers(), summary);
        return new Built(profile, inferences);
    }

    /** Plain summary used when there is no model output. */
    public static String templateSummary(TravelProfile p) {
        String pace = switch (p.pace()) {
            case "slow" -> "Slow-paced";
            case "packed" -> "Fast-paced";
            default -> "Balanced-pace";
        };
        Map<String, String> labels = Vocabulary.interestLabels();
        List<String> top = p.interests().entrySet().stream()
                .filter(e -> e.getValue() >= 0.5)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(3)
                .map(e -> labels.getOrDefault(e.getKey(), e.getKey()).toLowerCase(Locale.ROOT))
                .toList();
        String style = p.hiddenGems() >= 65 ? "prefers hidden gems over famous sights"
                : p.hiddenGems() <= 35 ? "likes the well-known classics"
                : "mixes classics with hidden gems";
        String drawn = top.isEmpty() ? "open to most things" : "drawn to " + joinNatural(top);
        return pace + " traveler " + drawn + "; " + style + ".";
    }

    private static List<String> mergeLists(List<String> fromQuiz, List<String> fromText, String field,
            List<Inference> inferences) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String item : fromQuiz) {
            String s = item.strip();
            if (!s.isEmpty() && seen.add(s.toLowerCase(Locale.ROOT))) out.add(s);
        }
        for (String item : fromText) {
            String s = item == null ? "" : item.strip();
            if (!s.isEmpty() && out.size() < 10 && seen.add(s.toLowerCase(Locale.ROOT))) {
                out.add(s);
                inferences.add(new Inference(field, s, "From your trip description"));
            }
        }
        return out;
    }

    private static List<String> distinct(List<String> items) {
        return new ArrayList<>(new LinkedHashSet<>(items));
    }

    /** A few words around a keyword match, cut on word boundaries, as evidence. */
    private static String snippet(String text, int start, int end) {
        int from = Math.max(0, start - 30);
        int to = Math.min(text.length(), end + 30);
        while (from > 0 && !Character.isWhitespace(text.charAt(from - 1))) from--;
        while (to < text.length() && !Character.isWhitespace(text.charAt(to))) to++;
        String s = text.substring(from, to).strip();
        return s.length() > 120 ? text.substring(start, end) : s;
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static String joinNatural(List<String> items) {
        if (items.size() == 1) return items.getFirst();
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.getLast();
    }

    /** Ordered map helper for prompts. */
    static Map<String, Integer> ordered(Map<String, Integer> in) {
        return new LinkedHashMap<>(new TreeMap<>(in));
    }
}
