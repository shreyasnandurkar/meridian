package com.meridian.fit;

import java.util.List;

/**
 * The anti-tourist-trap scorer (spec §4). Pluggable: scoring only depends on this interface, and the OSM-based
 * implementation can be swapped for one with better popularity data. It never sees commercial signals; there are none
 * in the input.
 */
public interface TouristTrapScorer {

    /**
     * @param fame    0–1, how famous the place looks (wiki links, translations)
     * @param trap    0–1, mass-tourism signals: fame plus chain brands and attraction tags
     * @param local   0–1, signs of a locally rooted, independent place
     * @param signals the evidence, in plain words, for "why" text
     */
    record Assessment(double fame, double trap, double local, List<String> signals) {}

    Assessment assess(PlaceSignals place);

    /**
     * Score adjustment for a hidden-gems ↔ classics setting (0 = classics, 100 = hidden gems). Gem seekers get local
     * places lifted and traps pushed down; classics fans get famous places lifted. 50 is neutral.
     */
    static double gemAdjustment(Assessment a, int hiddenGems) {
        double g = (Math.clamp(hiddenGems, 0, 100) - 50) / 50.0;
        return g >= 0
                ? g * (0.25 * a.local() - 0.35 * a.trap())
                : -g * 0.25 * a.fame();
    }
}
