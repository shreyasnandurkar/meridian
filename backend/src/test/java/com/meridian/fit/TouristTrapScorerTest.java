package com.meridian.fit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.meridian.place.OsmTags;
import com.meridian.place.PlaceCategory;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TouristTrapScorerTest {

    private final TouristTrapScorer scorer = new OsmTouristTrapScorer();

    private static PlaceSignals place(String name, PlaceCategory category, Map<String, String> tags) {
        return new PlaceSignals(name, category, PlaceInterests.of(tags, name, category), tags, OsmTags.popularity(tags));
    }

    @Test
    void famousChainsAreTrapsAndLocalPlacesAreLocal() {
        var chain = scorer.assess(place("Starbucks", PlaceCategory.FOOD,
                Map.of("amenity", "cafe", "brand", "Starbucks", "wikidata", "Q37158")));
        var local = scorer.assess(place("Ritz Classic", PlaceCategory.FOOD,
                Map.of("amenity", "restaurant", "cuisine", "goan;seafood")));

        assertThat(chain.trap()).isGreaterThan(0.6);
        assertThat(chain.signals()).contains("listed on Wikidata", "part of a chain (Starbucks)");
        assertThat(local.trap()).isZero();
        assertThat(local.local()).isGreaterThan(0.5);
        assertThat(local.signals()).contains("serves goan and seafood food");
    }

    @Test
    void sliderScalesTheAdjustment() {
        var trap = scorer.assess(place("Fort", PlaceCategory.SIGHTS, Map.of("historic", "fort", "tourism",
                "attraction", "wikipedia", "en:Fort", "wikidata", "Q1")));
        var gem = scorer.assess(place("Old well", PlaceCategory.SIGHTS, Map.of("historic", "well")));

        assertThat(TouristTrapScorer.gemAdjustment(trap, 50)).isCloseTo(0, within(1e-9));
        assertThat(TouristTrapScorer.gemAdjustment(trap, 100)).isNegative();
        assertThat(TouristTrapScorer.gemAdjustment(gem, 100)).isPositive();
        assertThat(TouristTrapScorer.gemAdjustment(trap, 0)).isPositive();
        assertThat(TouristTrapScorer.gemAdjustment(gem, 0)).isCloseTo(0, within(1e-9));
        // the weight grows with distance from the middle
        assertThat(TouristTrapScorer.gemAdjustment(gem, 100)).isGreaterThan(TouristTrapScorer.gemAdjustment(gem, 75));
    }

    @Test
    void placeInterestsComeFromTagsThenNameThenCategory() {
        assertThat(PlaceInterests.of(Map.of("natural", "beach"), "Kakolem", PlaceCategory.SIGHTS))
                .containsEntry("beaches", 1.0).containsEntry("nature", 0.4);
        assertThat(PlaceInterests.of(Map.of("craft", "pottery"), "Pottery studio", PlaceCategory.ACTIVITIES))
                .containsEntry("crafts", 1.0).containsEntry("local_culture", 0.5);
        assertThat(PlaceInterests.of(Map.of(), "Sunset kayak tour", PlaceCategory.ACTIVITIES))
                .containsEntry("water_sports", PlaceInterests.NAME_STRENGTH)
                .containsEntry("beaches", PlaceInterests.NAME_STRENGTH);
        assertThat(PlaceInterests.of(Map.of(), "Somewhere", PlaceCategory.FOOD)).containsEntry("local_food", 0.5);
        assertThat(PlaceInterests.of(Map.of("shop", "bakery"), "Confeitaria", PlaceCategory.FOOD))
                .doesNotContainKey("shopping");
    }
}
