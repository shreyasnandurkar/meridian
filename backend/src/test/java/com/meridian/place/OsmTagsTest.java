package com.meridian.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OsmTagsTest {

    @Test
    void classifiesByFirstMatchingCategory() {
        assertThat(OsmTags.categoryOf(Map.of("amenity", "restaurant", "cuisine", "goan")))
                .isEqualTo(PlaceCategory.FOOD);
        assertThat(OsmTags.categoryOf(Map.of("tourism", "guest_house"))).isEqualTo(PlaceCategory.STAYS);
        assertThat(OsmTags.categoryOf(Map.of("historic", "fort"))).isEqualTo(PlaceCategory.SIGHTS);
        assertThat(OsmTags.categoryOf(Map.of("natural", "beach"))).isEqualTo(PlaceCategory.SIGHTS);
        assertThat(OsmTags.categoryOf(Map.of("leisure", "nature_reserve"))).isEqualTo(PlaceCategory.ACTIVITIES);
        assertThat(OsmTags.categoryOf(Map.of("craft", "pottery"))).isEqualTo(PlaceCategory.ACTIVITIES);
        // a hotel in a heritage building is still somewhere to stay
        assertThat(OsmTags.categoryOf(Map.of("tourism", "hotel", "historic", "building")))
                .isEqualTo(PlaceCategory.STAYS);
        assertThat(OsmTags.categoryOf(Map.of("historic", "no"))).isEqualTo(PlaceCategory.OTHER);
        assertThat(OsmTags.categoryOf(Map.of("boundary", "administrative"))).isEqualTo(PlaceCategory.OTHER);
    }

    @Test
    void popularityRewardsWikiLinksAttractionsAndChains() {
        assertThat(OsmTags.popularity(Map.of("amenity", "cafe"))).isZero();
        assertThat(OsmTags.popularity(Map.of("brand", "Starbucks", "amenity", "cafe"))).isEqualTo(2);
        assertThat(OsmTags.popularity(Map.of("tourism", "attraction", "wikipedia", "en:Basilica of Bom Jesus",
                "wikidata", "Q15177"))).isEqualTo(9);
        assertThat(OsmTags.popularity(Map.of("wikipedia", "x", "wikidata", "Q1", "tourism", "attraction",
                "brand", "b", "name:en", "a", "name:hi", "b", "name:fr", "c"))).isEqualTo(10);
    }

    @Test
    void buildsAddressFromAddrTags() {
        assertThat(OsmTags.address(Map.of("addr:street", "Rua de Ourém", "addr:housenumber", "12",
                "addr:city", "Panaji"))).isEqualTo("12 Rua de Ourém, Panaji");
        assertThat(OsmTags.address(Map.of("addr:place", "Assagao"))).isEqualTo("Assagao");
        assertThat(OsmTags.address(Map.of())).isNull();
    }

    @Test
    void websiteGetsAScheme() {
        assertThat(OsmTags.website(Map.of("website", "example.com"))).isEqualTo("https://example.com");
        assertThat(OsmTags.website(Map.of("contact:website", "http://a.in/"))).isEqualTo("http://a.in/");
    }

    @Test
    void overpassQueryUsesTheSameRules() {
        String q = OsmTags.overpassQuery(PlaceCategory.FOOD, 15.5, 73.8, 5000, 300, 25);
        assertThat(q).startsWith("[out:json][timeout:25];")
                .contains("nwr[\"amenity\"~\"^(restaurant|cafe|bar|pub|fast_food|food_court|ice_cream|biergarten)$\"]"
                        + "[\"name\"](around:5000,15.500000,73.800000);")
                .endsWith("out center tags qt 300;");
        assertThat(OsmTags.overpassQuery(PlaceCategory.SIGHTS, 0, 0, 1, 1, 1)).contains("nwr[\"historic\"][\"name\"]");
    }
}
