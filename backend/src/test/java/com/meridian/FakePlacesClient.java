package com.meridian;

import com.meridian.place.PlaceCategory;
import com.meridian.place.PlacesClient;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Stands in for OpenStreetMap in integration tests: canned answers, a call counter and an outage switch. */
public class FakePlacesClient implements PlacesClient {

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        FakePlacesClient fakePlacesClient() {
            return new FakePlacesClient();
        }
    }

    public final AtomicInteger calls = new AtomicInteger();
    public volatile boolean down;
    public volatile List<PlaceData> answer = List.of();

    public void reset(List<PlaceData> answer) {
        this.answer = answer;
        this.down = false;
        this.calls.set(0);
    }

    public static PlaceData place(String sourceId, String name, PlaceCategory category, double lat, double lng) {
        return new PlaceData("osm", sourceId, name, category, lat, lng, null, null, Map.of("name", name), 0);
    }

    @Override
    public List<PlaceData> nearby(double lat, double lng, PlaceCategory category, int radiusKm, int limit) {
        return respond();
    }

    @Override
    public List<PlaceData> search(String query, Double lat, Double lng, int radiusKm, int limit) {
        return respond();
    }

    private List<PlaceData> respond() {
        calls.incrementAndGet();
        if (down) throw new PlacesUnavailableException("fake outage", null);
        return answer;
    }
}
