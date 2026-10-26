package com.meridian.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.meridian.common.ApiException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * OpenStreetMap Nominatim. Follows the public usage policy: identifying User-Agent, at most one request per second,
 * results cached so typing the same prefix twice never hits the service again.
 */
@Component
public class NominatimGeocodingClient implements GeocodingClient {

    private static final Logger log = LoggerFactory.getLogger(NominatimGeocodingClient.class);
    private static final long MIN_INTERVAL_MS = 1100;
    private static final int CACHE_SIZE = 500;

    private final RestClient http;
    private final Map<String, List<GeoResult>> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<GeoResult>> eldest) {
                    return size() > CACHE_SIZE;
                }
            });
    private final Object throttle = new Object();
    private long lastCallAt;

    public NominatimGeocodingClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(6));
        this.http = RestClient.builder()
                .baseUrl("https://nominatim.openstreetmap.org")
                .requestFactory(factory)
                .defaultHeader("User-Agent", "Meridian/0.2 (collaborative trip planner; classroom project)")
                .defaultHeader("Accept-Language", "en")
                .build();
    }

    @Override
    public List<GeoResult> search(String query) {
        String key = query.strip().toLowerCase(Locale.ROOT);
        List<GeoResult> cached = cache.get(key);
        if (cached != null) return cached;

        JsonNode body;
        try {
            waitForSlot();
            body = http.get()
                    .uri(u -> u.path("/search")
                            .queryParam("q", query.strip())
                            .queryParam("format", "jsonv2")
                            .queryParam("limit", 6)
                            .queryParam("addressdetails", 0)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("Nominatim search failed: {}", e.getMessage());
            throw ApiException.badGateway("Destination search is unavailable right now. You can type the name instead.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.badGateway("Destination search was interrupted");
        }

        List<GeoResult> results = new ArrayList<>();
        if (body != null && body.isArray()) {
            for (JsonNode n : body) {
                String display = n.path("display_name").asText();
                String name = n.path("name").asText("");
                results.add(new GeoResult(name.isBlank() ? display.split(",")[0] : name, display,
                        n.path("lat").asDouble(), n.path("lon").asDouble(), n.path("addresstype").asText(
                                n.path("type").asText(""))));
            }
        }
        List<GeoResult> immutable = List.copyOf(results);
        cache.put(key, immutable);
        return immutable;
    }

    private void waitForSlot() throws InterruptedException {
        synchronized (throttle) {
            long wait = lastCallAt + MIN_INTERVAL_MS - System.currentTimeMillis();
            if (wait > 0) Thread.sleep(wait);
            lastCallAt = System.currentTimeMillis();
        }
    }
}
