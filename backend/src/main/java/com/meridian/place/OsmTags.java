package com.meridian.place;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OpenStreetMap tag rules: which tags make a place a sight, food, stay or activity, and how famous it looks. The same
 * rules build the Overpass query and classify its answers, so the two can never disagree.
 */
public final class OsmTags {

    /** A tag key with allowed values; an empty list means any value. */
    record Rule(String key, List<String> values) {

        boolean matches(Map<String, String> tags) {
            String v = tags.get(key);
            if (v == null || v.isBlank() || v.equals("no")) return false;
            return values.isEmpty() || values.contains(v);
        }

        String overpassFilter() {
            return values.isEmpty()
                    ? "[\"" + key + "\"]"
                    : "[\"" + key + "\"~\"^(" + String.join("|", values) + ")$\"]";
        }
    }

    /** Checked in this order; the first category with a matching rule wins. */
    static final Map<PlaceCategory, List<Rule>> RULES = new LinkedHashMap<>();

    static {
        RULES.put(PlaceCategory.STAYS, List.of(
                new Rule("tourism", List.of("hotel", "guest_house", "hostel", "motel", "apartment", "chalet",
                        "camp_site")),
                new Rule("leisure", List.of("resort"))));
        RULES.put(PlaceCategory.FOOD, List.of(
                new Rule("amenity", List.of("restaurant", "cafe", "bar", "pub", "fast_food", "food_court",
                        "ice_cream", "biergarten")),
                new Rule("shop", List.of("bakery", "confectionery"))));
        RULES.put(PlaceCategory.SIGHTS, List.of(
                new Rule("tourism", List.of("attraction", "museum", "gallery", "viewpoint", "artwork")),
                new Rule("historic", List.of()),
                new Rule("amenity", List.of("place_of_worship")),
                new Rule("natural", List.of("beach", "waterfall", "peak", "cave_entrance", "hot_spring"))));
        RULES.put(PlaceCategory.ACTIVITIES, List.of(
                new Rule("tourism", List.of("theme_park", "zoo", "aquarium")),
                new Rule("leisure", List.of("park", "nature_reserve", "water_park", "marina", "garden",
                        "sports_centre")),
                new Rule("amenity", List.of("arts_centre", "theatre", "cinema", "dive_centre", "nightclub",
                        "marketplace")),
                new Rule("sport", List.of("scuba_diving", "surfing", "kitesurfing")),
                new Rule("craft", List.of())));
    }

    /**
     * Nominatim special phrases per category, used for "nearby" when Overpass is unavailable. Results are still
     * classified by {@link #RULES}, so a phrase can only add places that belong to its category.
     */
    static final Map<PlaceCategory, List<String>> NOMINATIM_PHRASES = Map.of(
            PlaceCategory.SIGHTS, List.of("museum", "fort", "beach", "viewpoint", "temple", "church"),
            PlaceCategory.FOOD, List.of("restaurant", "cafe", "bakery"),
            PlaceCategory.STAYS, List.of("guest house", "hotel", "hostel"),
            PlaceCategory.ACTIVITIES, List.of("nature reserve", "park", "market", "dive centre"));

    private OsmTags() {}

    public static PlaceCategory categoryOf(Map<String, String> tags) {
        for (var entry : RULES.entrySet()) {
            for (Rule rule : entry.getValue()) {
                if (rule.matches(tags)) return entry.getKey();
            }
        }
        return PlaceCategory.OTHER;
    }

    /** Overpass QL for named places of one category around a point. */
    static String overpassQuery(PlaceCategory category, double lat, double lng, int radiusM, int maxResults,
            int timeoutSeconds) {
        List<Rule> rules = RULES.get(category);
        if (rules == null) throw new IllegalArgumentException("No OSM rules for " + category);
        String around = String.format(Locale.ROOT, "(around:%d,%.6f,%.6f)", radiusM, lat, lng);
        StringBuilder q = new StringBuilder("[out:json][timeout:").append(timeoutSeconds).append("];\n(\n");
        for (Rule rule : rules) {
            q.append("  nwr").append(rule.overpassFilter()).append("[\"name\"]").append(around).append(";\n");
        }
        return q.append(");\nout center tags qt ").append(maxResults).append(";").toString();
    }

    /**
     * Popularity proxy, 0–10: wiki articles, mass-tourism attraction tags, chain brands and many translated names all
     * suggest a place every visitor already goes to. Used by the anti-tourist-trap scorer.
     */
    public static int popularity(Map<String, String> tags) {
        int score = 0;
        if (tags.containsKey("wikipedia")) score += 4;
        if (tags.containsKey("wikidata")) score += 3;
        if ("attraction".equals(tags.get("tourism"))) score += 2;
        if (tags.containsKey("brand") || tags.containsKey("brand:wikidata")) score += 2;
        long translations = tags.keySet().stream().filter(k -> k.startsWith("name:")).count();
        score += (int) Math.min(3, translations / 3);
        return Math.min(10, score);
    }

    /** "12 Rua de Ourém, Panaji" from addr:* tags, or null. */
    static String address(Map<String, String> tags) {
        List<String> parts = new ArrayList<>();
        String street = tags.get("addr:street");
        String number = tags.get("addr:housenumber");
        if (street != null) parts.add(number != null ? number + " " + street : street);
        String locality = firstNonBlank(tags.get("addr:place"), tags.get("addr:suburb"), tags.get("addr:city"));
        if (locality != null) parts.add(locality);
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    static String website(Map<String, String> tags) {
        String url = firstNonBlank(tags.get("website"), tags.get("contact:website"), tags.get("url"));
        if (url == null) return null;
        String u = url.strip();
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
        return u.length() > 2048 ? null : u;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.strip();
        }
        return null;
    }
}
