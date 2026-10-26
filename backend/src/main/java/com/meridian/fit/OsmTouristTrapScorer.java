package com.meridian.fit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Anti-tourist-trap scoring from OpenStreetMap tags. Fame comes from the popularity proxy (Wikipedia and Wikidata
 * links, mass-tourism attraction tags, chain brands, many translated names); localness from regional cuisine, craft
 * workshops, guest houses, markets and heritage sites nobody has written up yet.
 */
@Component
public class OsmTouristTrapScorer implements TouristTrapScorer {

    private static final Set<String> SMALL_STAYS = Set.of("guest_house", "camp_site", "chalet");

    @Override
    public Assessment assess(PlaceSignals p) {
        Map<String, String> tags = p.tags();
        List<String> signals = new ArrayList<>();
        double fame = Math.clamp(p.popularity() / 10.0, 0, 1);
        boolean chain = p.isChain();
        boolean attraction = "attraction".equals(tags.get("tourism"));

        if (tags.containsKey("wikipedia")) signals.add("has a Wikipedia article");
        else if (tags.containsKey("wikidata")) signals.add("listed on Wikidata");
        if (chain) signals.add("part of a chain (" + tags.getOrDefault("brand", "brand") + ")");
        if (attraction) signals.add("tagged as a tourist attraction");
        double trap = Math.min(1, 0.6 * fame + (chain ? 0.35 : 0) + (attraction ? 0.1 : 0));

        double local = 0;
        Set<String> localCuisines = PlaceInterests.cuisines(tags).stream()
                .filter(PlaceInterests.LOCAL_CUISINES::contains).collect(Collectors.toSet());
        if (!localCuisines.isEmpty()) {
            local += 0.4;
            signals.add("serves " + String.join(" and ", localCuisines.stream().sorted().toList()).replace('_', ' ')
                    + " food");
        }
        if (tags.containsKey("craft")) {
            local += 0.4;
            signals.add("a working " + tags.get("craft").replace('_', ' ') + " workshop");
        }
        if (SMALL_STAYS.contains(tags.getOrDefault("tourism", ""))) {
            local += 0.3;
            signals.add("a small " + tags.get("tourism").replace('_', ' '));
        }
        if ("marketplace".equals(tags.get("amenity"))) {
            local += 0.2;
            signals.add("a local market");
        }
        if ((tags.containsKey("historic") || tags.containsKey("heritage")) && fame < 0.4) {
            local += 0.3;
            signals.add("heritage few visitors know");
        }
        if (!chain && fame < 0.3) {
            local += 0.15;
            if (signals.isEmpty()) signals.add("independent and little-known");
        }
        return new Assessment(fame, trap, Math.min(1, local), List.copyOf(signals));
    }
}
