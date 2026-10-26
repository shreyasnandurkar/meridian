package com.meridian.fit;

import com.meridian.place.PlaceCategory;
import com.meridian.profile.Vocabulary;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Translates what OpenStreetMap says about a place into the profile interest vocabulary, as strengths in [0, 1]. A
 * beach is mostly "beaches" and a little "nature"; a craft workshop is "crafts" and some "local_culture". Members'
 * interest weights are matched against these strengths, so places and profiles speak the same language.
 */
public final class PlaceInterests {

    /** Strength given to an interest found only in a place's name or a link's title. */
    static final double NAME_STRENGTH = 0.5;

    /** Cuisines that mark a restaurant as rooted in the region rather than international. */
    static final Set<String> LOCAL_CUISINES = Set.of("goan", "konkani", "malvani", "indian", "regional", "local",
            "south_indian", "kerala", "mangalorean", "maharashtrian", "seafood", "fish", "thali", "udupi",
            "portuguese", "bengali", "gujarati", "punjabi", "rajasthani", "hyderabadi", "chettinad", "coastal");

    /** Cuisines that are plainly from elsewhere: a pizzeria in Panaji is food, but not local food. */
    static final Set<String> INTERNATIONAL_CUISINES = Set.of("pizza", "burger", "sushi", "japanese", "chinese",
            "italian", "mexican", "thai", "korean", "american", "french", "lebanese", "middle_eastern",
            "mediterranean", "continental", "kebab", "sandwich", "asian", "tibetan", "israeli", "greek", "turkish",
            "vietnamese", "chicken", "fried_chicken");

    /** Untagged restaurants often say what they serve in the name ("Yaki Zushi", "Pizza Hut"). */
    private static final Pattern INTERNATIONAL_NAME = Pattern.compile("\\b(pizza|pizzeria|sushi|zushi|ramen|chinese"
            + "|italian|burgers?|dominoe?'?s|kfc|mcdonald'?s|subway|thai|korean|mexican|lebanese|shawarma"
            + "|middle eastern|continental|tibetan|momos?)\\b", Pattern.CASE_INSENSITIVE);

    private record Rule(Predicate<Map<String, String>> when, Map<String, Double> interests) {}

    private static final List<Rule> RULES = List.of(
            new Rule(t -> "restaurant".equals(t.get("amenity")) && !international(t), Map.of("local_food", 0.7)),
            new Rule(t -> "restaurant".equals(t.get("amenity")) && international(t), Map.of("local_food", 0.2)),
            is("amenity", "food_court", "street_food", 0.7, "local_food", 0.4),
            is("amenity", "fast_food", "street_food", 0.6),
            is("amenity", "cafe", "cafes", 1.0),
            is("amenity", "ice_cream", "street_food", 0.3, "cafes", 0.3),
            is("shop", "bakery", "cafes", 0.6, "local_food", 0.4),
            is("shop", "confectionery", "local_food", 0.4, "street_food", 0.3),
            is("amenity", "bar", "nightlife", 1.0),
            is("amenity", "pub", "nightlife", 1.0),
            is("amenity", "biergarten", "nightlife", 0.8),
            is("amenity", "nightclub", "nightlife", 1.0, "music", 0.5),
            is("live_music", "yes", "music", 0.8),
            has("historic", "history", 1.0),
            has("heritage", "history", 0.6),
            is("historic", "fort", "architecture", 0.6, "photography", 0.4),
            is("historic", "castle", "architecture", 0.6, "photography", 0.4),
            is("historic", "ruins", "photography", 0.5),
            is("historic", "monument", "architecture", 0.5),
            is("amenity", "place_of_worship", "spiritual", 0.8, "architecture", 0.6, "local_culture", 0.4),
            is("tourism", "museum", "museums", 1.0, "history", 0.5),
            is("tourism", "gallery", "art", 1.0, "museums", 0.6),
            is("tourism", "artwork", "art", 0.8, "photography", 0.3),
            is("tourism", "viewpoint", "photography", 0.9, "nature", 0.6),
            is("tourism", "zoo", "wildlife", 0.8),
            is("tourism", "aquarium", "wildlife", 0.8),
            is("tourism", "theme_park", "adventure", 0.7),
            is("tourism", "guest_house", "local_culture", 0.5),
            is("tourism", "hostel", "nightlife", 0.3, "local_culture", 0.2),
            is("tourism", "camp_site", "nature", 0.7, "adventure", 0.4),
            is("tourism", "chalet", "nature", 0.4),
            is("natural", "beach", "beaches", 1.0, "nature", 0.4, "water_sports", 0.2),
            is("natural", "waterfall", "nature", 1.0, "hiking", 0.6, "photography", 0.5),
            is("natural", "peak", "hiking", 1.0, "nature", 0.7, "photography", 0.4),
            is("natural", "cave_entrance", "adventure", 0.6, "nature", 0.6),
            is("natural", "hot_spring", "wellness", 0.8, "nature", 0.5),
            is("leisure", "park", "nature", 0.5, "wellness", 0.2),
            is("leisure", "garden", "nature", 0.6, "photography", 0.3),
            is("leisure", "nature_reserve", "nature", 1.0, "wildlife", 0.8, "hiking", 0.6),
            is("leisure", "water_park", "adventure", 0.6, "water_sports", 0.5),
            is("leisure", "marina", "water_sports", 0.6),
            is("leisure", "sports_centre", "adventure", 0.3),
            is("leisure", "resort", "wellness", 0.6, "beaches", 0.3),
            is("amenity", "arts_centre", "art", 0.8, "local_culture", 0.6, "music", 0.4),
            is("amenity", "theatre", "local_culture", 0.7, "music", 0.5, "art", 0.5),
            is("amenity", "cinema", "local_culture", 0.2),
            is("amenity", "dive_centre", "water_sports", 1.0, "adventure", 0.6, "wildlife", 0.4),
            is("amenity", "marketplace", "markets", 1.0, "local_culture", 0.7, "street_food", 0.4, "shopping", 0.4),
            new Rule(t -> t.containsKey("sport") && Set.of("scuba_diving", "surfing", "kitesurfing", "kayaking",
                    "canoe").contains(t.get("sport")), Map.of("water_sports", 1.0, "adventure", 0.7)),
            has("craft", "crafts", 1.0, "local_culture", 0.5, "shopping", 0.3),
            new Rule(t -> t.containsKey("shop") && !Set.of("bakery", "confectionery", "no").contains(t.get("shop")),
                    Map.of("shopping", 0.6)),
            new Rule(t -> cuisines(t).stream().anyMatch(LOCAL_CUISINES::contains), Map.of("local_food", 1.0)),
            new Rule(t -> cuisines(t).contains("coffee_shop"), Map.of("cafes", 1.0)));

    /** When no tag says anything, the category alone hints at what a place offers. */
    private static final Map<PlaceCategory, Map<String, Double>> CATEGORY_DEFAULTS = Map.of(
            PlaceCategory.FOOD, Map.of("local_food", 0.5),
            PlaceCategory.SIGHTS, Map.of("local_culture", 0.3),
            PlaceCategory.ACTIVITIES, Map.of("adventure", 0.3));

    private PlaceInterests() {}

    /**
     * Interest strengths for a place. Tag rules win; words in the name (or a link's title, which is all we know about
     * links and pins) add weaker signals; the category is the last resort.
     */
    public static Map<String, Double> of(Map<String, String> tags, String name, PlaceCategory category) {
        Map<String, Double> out = new TreeMap<>();
        Map<String, String> named = tags;
        if (name != null && !tags.containsKey("name")) {
            named = new HashMap<>(tags);
            named.put("name", name);
        }
        for (Rule rule : RULES) {
            if (rule.when().test(named)) rule.interests().forEach((tag, s) -> out.merge(tag, s, Math::max));
        }
        for (String tag : Vocabulary.interestsMentioned(name)) out.merge(tag, NAME_STRENGTH, Math::max);
        if (out.isEmpty()) out.putAll(CATEGORY_DEFAULTS.getOrDefault(category, Map.of()));
        return out;
    }

    /** {@code cuisine=goan;seafood} as a set of lower-case values. */
    static Set<String> cuisines(Map<String, String> tags) {
        String c = tags.get("cuisine");
        if (c == null || c.isBlank()) return Set.of();
        return Set.copyOf(Arrays.stream(c.toLowerCase(Locale.ROOT).split("[;,]"))
                .map(String::strip).filter(s -> !s.isEmpty()).toList());
    }

    /** Serves food from elsewhere: by its cuisine tags when it has any, otherwise by its name. */
    static boolean international(Map<String, String> tags) {
        Set<String> cuisines = cuisines(tags);
        if (!cuisines.isEmpty()) {
            return cuisines.stream().noneMatch(LOCAL_CUISINES::contains)
                    && cuisines.stream().anyMatch(INTERNATIONAL_CUISINES::contains);
        }
        String name = tags.get("name");
        return name != null && INTERNATIONAL_NAME.matcher(name).find();
    }

    private static Rule is(String key, String value, Object... interests) {
        return new Rule(t -> value.equals(t.get(key)), strengths(interests));
    }

    private static Rule has(String key, Object... interests) {
        return new Rule(t -> {
            String v = t.get(key);
            return v != null && !v.isBlank() && !v.equals("no");
        }, strengths(interests));
    }

    private static Map<String, Double> strengths(Object... pairs) {
        Map<String, Double> m = new TreeMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], (Double) pairs[i + 1]);
        return Map.copyOf(m);
    }
}
