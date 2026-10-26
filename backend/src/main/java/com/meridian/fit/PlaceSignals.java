package com.meridian.fit;

import com.meridian.place.Place;
import com.meridian.place.PlaceCategory;
import com.meridian.place.PlaceDtos.PlaceView;
import java.util.Map;

/**
 * Everything scoring knows about one place: its interest strengths (see {@link PlaceInterests}), raw OSM tags and the
 * popularity proxy. Links and pins only have a title and a category, so their signals are thinner.
 */
public record PlaceSignals(String name, PlaceCategory category, Map<String, Double> interests,
        Map<String, String> tags, int popularity) {

    public static PlaceSignals of(Place p) {
        return new PlaceSignals(p.getName(), p.getCategory(),
                PlaceInterests.of(p.getTags(), p.getName(), p.getCategory()), p.getTags(), p.getPopularityProxy());
    }

    public static PlaceSignals of(PlaceView p) {
        return new PlaceSignals(p.name(), p.category(), PlaceInterests.of(p.tags(), p.name(), p.category()), p.tags(),
                p.popularityProxy());
    }

    /** A pasted link or a dropped pin: all we have is what the group called it. */
    public static PlaceSignals titled(String title, PlaceCategory category) {
        return new PlaceSignals(title, category, PlaceInterests.of(Map.of(), title, category), Map.of(), 0);
    }

    public double strength(String interest) {
        return interests.getOrDefault(interest, 0.0);
    }

    public boolean isChain() {
        return tags.containsKey("brand") || tags.containsKey("brand:wikidata");
    }
}
