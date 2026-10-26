package com.meridian.profile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Fixed vocabularies for traveler profiles. Interest tags are what place scoring (A2) matches against, so the model
 * may only use tags from this list. Keywords drive the rule-based reading of the free-text answer.
 */
public final class Vocabulary {

    private Vocabulary() {}

    public record Interest(String tag, String label, boolean headline, List<String> keywords) {}

    public record Option(String id, String label) {}

    public static final List<Interest> INTERESTS = List.of(
            new Interest("local_food", "Local food", true,
                    List.of("local food", "cuisine", "seafood", "curry", "thali", "home-cooked", "home cooked", "foodie")),
            new Interest("street_food", "Street food", false, List.of("street food", "food stall", "snack")),
            new Interest("cafes", "Cafés", false, List.of("cafe", "café", "coffee", "brunch", "bakery", "bakeries")),
            new Interest("nightlife", "Nightlife", true,
                    List.of("nightlife", "party", "parties", "club", "bar", "pub", "dance", "dancing")),
            new Interest("history", "History & heritage", true,
                    List.of("history", "heritage", "fort", "ruins", "colonial", "old town", "old quarter")),
            new Interest("architecture", "Architecture", false,
                    List.of("architecture", "church", "churches", "temple", "mansion", "building")),
            new Interest("museums", "Museums & galleries", false, List.of("museum", "gallery", "galleries")),
            new Interest("art", "Art & design", true, List.of("art", "mural", "design", "street art")),
            new Interest("crafts", "Crafts & makers", false,
                    List.of("craft", "artisan", "pottery", "handmade", "workshop", "weaving")),
            new Interest("markets", "Markets", true, List.of("market", "bazaar", "flea")),
            new Interest("nature", "Nature", true,
                    List.of("nature", "forest", "waterfall", "greenery", "plantation", "countryside", "river")),
            new Interest("beaches", "Beaches", true, List.of("beach", "beaches", "coast", "sand", "sunset", "sea")),
            new Interest("hiking", "Hiking & walks", false, List.of("hike", "hiking", "trek", "trekking", "trail", "walking")),
            new Interest("wildlife", "Wildlife", false,
                    List.of("wildlife", "bird", "birding", "dolphin", "animals", "sanctuary")),
            new Interest("water_sports", "Water sports", false,
                    List.of("surf", "surfing", "kayak", "kayaking", "snorkel", "snorkeling", "snorkelling", "diving", "scuba",
                            "water sport", "paddle", "paddleboarding")),
            new Interest("adventure", "Adventure", true,
                    List.of("adventure", "adrenaline", "paragliding", "zipline", "bungee", "thrill")),
            new Interest("wellness", "Wellness", true,
                    List.of("yoga", "spa", "wellness", "retreat", "relax", "ayurveda", "massage")),
            new Interest("photography", "Photography", false, List.of("photo", "photography", "camera")),
            new Interest("music", "Live music", false, List.of("music", "gig", "concert", "live band", "jazz")),
            new Interest("local_culture", "Local life & culture", true,
                    List.of("local life", "locals", "village", "culture", "festival", "community", "authentic")),
            new Interest("spiritual", "Spiritual places", false,
                    List.of("spiritual", "pilgrimage", "meditation", "monastery", "shrine")),
            new Interest("shopping", "Shopping", false, List.of("shopping", "boutique", "souvenir")));

    public static final List<Option> PACES = List.of(
            new Option("slow", "Slow — one or two things a day"),
            new Option("balanced", "Balanced — a plan with breathing room"),
            new Option("packed", "Packed — see as much as possible"));

    public static final List<Option> BUDGET_LEVELS = List.of(
            new Option("low", "Budget"), new Option("mid", "Mid-range"), new Option("high", "Splurge"));

    public static final List<Option> DIETARY = List.of(
            new Option("vegetarian", "Vegetarian"), new Option("vegan", "Vegan"), new Option("jain", "Jain"),
            new Option("halal", "Halal"), new Option("gluten_free", "Gluten-free"),
            new Option("lactose_free", "Lactose-free"), new Option("no_seafood", "No seafood"),
            new Option("no_alcohol", "No alcohol"));

    public static final List<Option> ACCESS_NEEDS = List.of(
            new Option("limited_walking", "Limited walking"), new Option("step_free", "Step-free access"),
            new Option("wheelchair", "Wheelchair access"), new Option("quiet_spaces", "Quiet spaces"),
            new Option("child_friendly", "Child-friendly"));

    public static final Set<String> INTEREST_TAGS = INTERESTS.stream().map(Interest::tag).collect(Collectors.toUnmodifiableSet());
    public static final Set<String> HEADLINE_TAGS =
            INTERESTS.stream().filter(Interest::headline).map(Interest::tag).collect(Collectors.toUnmodifiableSet());
    public static final Set<String> PACE_IDS = ids(PACES);
    public static final Set<String> BUDGET_IDS = ids(BUDGET_LEVELS);
    public static final Set<String> DIETARY_IDS = ids(DIETARY);
    public static final Set<String> ACCESS_IDS = ids(ACCESS_NEEDS);

    public static Map<String, String> interestLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        INTERESTS.forEach(i -> labels.put(i.tag(), i.label()));
        return labels;
    }

    /** Whole-word, case-insensitive match for a keyword, allowing a plural ending. */
    public static Pattern keywordPattern(String keyword) {
        return Pattern.compile("\\b" + Pattern.quote(keyword) + "(?:s|es)?\\b",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /** Interest tags whose keywords (or label) appear in free text, in vocabulary order. */
    public static List<String> interestsMentioned(String text) {
        if (text == null || text.isBlank()) return List.of();
        return INTERESTS.stream()
                .filter(i -> keywordPattern(i.label()).matcher(text).find()
                        || i.keywords().stream().anyMatch(k -> keywordPattern(k).matcher(text).find()))
                .map(Interest::tag)
                .toList();
    }

    public static String label(List<Option> options, String id) {
        return options.stream().filter(o -> o.id().equals(id)).map(Option::label).findFirst().orElse(id);
    }

    private static Set<String> ids(List<Option> options) {
        return options.stream().map(Option::id).collect(Collectors.toUnmodifiableSet());
    }
}
