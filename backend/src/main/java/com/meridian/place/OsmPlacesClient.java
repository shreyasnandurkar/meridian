package com.meridian.place;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.config.MeridianProperties;
import com.meridian.geo.GeoMath;
import com.meridian.geo.OsmRateLimiter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * OpenStreetMap places: Overpass for "everything of this kind nearby", Nominatim for free-text search. Both get the
 * identifying User-Agent; Nominatim calls share the app-wide one-per-second limit.
 */
@Component
@ConditionalOnProperty(name = "meridian.places.provider", havingValue = "osm", matchIfMissing = true)
public class OsmPlacesClient implements PlacesClient {

    static final String SOURCE = "osm";
    private static final Logger log = LoggerFactory.getLogger(OsmPlacesClient.class);
    /** Budget for all Overpass mirrors together before falling back to Nominatim. */
    static final Duration OVERPASS_BUDGET = Duration.ofSeconds(12);
    /** Overpass returns elements in quadtile order, so ask for more than needed and keep the nearest. */
    private static final int OVERPASS_OVERFETCH = 5;
    /** Nominatim's per-request maximum. */
    private static final int NOMINATIM_MAX = 40;

    private final RestClient overpass;
    private final List<String> overpassUrls;
    private final RestClient nominatim;
    private final OsmRateLimiter rateLimiter;
    private final ObjectMapper json = new ObjectMapper();

    public OsmPlacesClient(MeridianProperties props, OsmRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.overpassUrls = Arrays.stream(props.places().overpassUrl().split(","))
                .map(String::strip)
                .filter(u -> !u.isEmpty())
                .toList();
        SimpleClientHttpRequestFactory slow = new SimpleClientHttpRequestFactory();
        slow.setConnectTimeout(Duration.ofSeconds(4));
        slow.setReadTimeout(OVERPASS_BUDGET);
        this.overpass = RestClient.builder()
                .requestFactory(slow)
                .defaultHeader("User-Agent", OsmRateLimiter.USER_AGENT)
                .build();
        SimpleClientHttpRequestFactory fast = new SimpleClientHttpRequestFactory();
        fast.setConnectTimeout(Duration.ofSeconds(4));
        fast.setReadTimeout(Duration.ofSeconds(8));
        this.nominatim = RestClient.builder()
                .baseUrl(props.places().nominatimUrl())
                .requestFactory(fast)
                .defaultHeader("User-Agent", OsmRateLimiter.USER_AGENT)
                .defaultHeader("Accept-Language", "en")
                .build();
    }

    /**
     * Overpass first (complete data); the public instances are often at capacity, so after the configured mirrors
     * fail or run out of time, Nominatim category phrases ("museum", "beach", ...) give a smaller but solid list.
     */
    @Override
    public List<PlaceData> nearby(double lat, double lng, PlaceCategory category, int radiusKm, int limit) {
        List<PlaceData> places;
        try {
            places = overpassNearby(lat, lng, category, radiusKm, limit);
        } catch (PlacesUnavailableException e) {
            log.info("Overpass unavailable, using Nominatim category search for {}", category);
            places = nominatimNearby(lat, lng, category, radiusKm);
        }
        Map<String, PlaceData> unique = new LinkedHashMap<>();
        places.stream()
                .filter(p -> p.category() == category)
                .forEach(p -> unique.putIfAbsent(p.sourceId(), p));
        return unique.values().stream()
                .sorted(Comparator.comparingDouble(p -> GeoMath.distanceKm(lat, lng, p.lat(), p.lng())))
                .limit(limit)
                .toList();
    }

    private List<PlaceData> overpassNearby(double lat, double lng, PlaceCategory category, int radiusKm, int limit) {
        String query = OsmTags.overpassQuery(category, lat, lng, radiusKm * 1000, limit * OVERPASS_OVERFETCH,
                (int) OVERPASS_BUDGET.toSeconds());
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("data", query);
        Instant deadline = Instant.now().plus(OVERPASS_BUDGET);
        RuntimeException last = null;
        for (String url : overpassUrls) {
            if (Instant.now().isAfter(deadline)) break;
            try {
                // read as text: overloaded instances answer with odd content types
                String body = overpass.post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(String.class);
                JsonNode node = json.readTree(body == null ? "" : body);
                String remark = node.path("remark").asText("");
                if (!node.has("elements") || (node.path("elements").isEmpty() && !remark.isEmpty())) {
                    throw new IllegalStateException("Overpass gave no result: " + remark);
                }
                return parseOverpass(node);
            } catch (RestClientException | IOException | IllegalStateException e) {
                log.warn("Overpass query to {} failed: {}", url, e.getMessage());
                last = new RuntimeException(e);
            }
        }
        throw new PlacesUnavailableException("OpenStreetMap (Overpass) is unavailable", last);
    }

    private List<PlaceData> nominatimNearby(double lat, double lng, PlaceCategory category, int radiusKm) {
        List<PlaceData> out = new ArrayList<>();
        PlacesUnavailableException failure = null;
        for (String phrase : OsmTags.NOMINATIM_PHRASES.getOrDefault(category, List.of())) {
            try {
                out.addAll(search(phrase, lat, lng, radiusKm, NOMINATIM_MAX));
            } catch (PlacesUnavailableException e) {
                failure = e;
            }
        }
        if (out.isEmpty() && failure != null) throw failure;
        return out;
    }

    @Override
    public List<PlaceData> search(String query, Double lat, Double lng, int radiusKm, int limit) {
        JsonNode body;
        try {
            rateLimiter.acquire();
            body = nominatim.get()
                    .uri(u -> {
                        u.path("/search")
                                .queryParam("q", query.strip())
                                .queryParam("format", "jsonv2")
                                .queryParam("limit", limit)
                                .queryParam("extratags", 1)
                                .queryParam("addressdetails", 0);
                        if (lat != null && lng != null) {
                            u.queryParam("viewbox", viewbox(lat, lng, radiusKm)).queryParam("bounded", 1);
                        }
                        return u.build();
                    })
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("Nominatim place search failed: {}", e.getMessage());
            throw new PlacesUnavailableException("OpenStreetMap (Nominatim) is unavailable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PlacesUnavailableException("Place search was interrupted", e);
        }
        return parseNominatim(body);
    }

    /** left,top,right,bottom around a point. */
    static String viewbox(double lat, double lng, int radiusKm) {
        double dLat = radiusKm / 111.32;
        double dLng = radiusKm / (111.32 * Math.max(0.1, Math.cos(Math.toRadians(lat))));
        return String.format(Locale.ROOT, "%.5f,%.5f,%.5f,%.5f", lng - dLng, lat + dLat, lng + dLng, lat - dLat);
    }

    static List<PlaceData> parseOverpass(JsonNode body) {
        List<PlaceData> out = new ArrayList<>();
        if (body == null) return out;
        for (JsonNode el : body.path("elements")) {
            Map<String, String> tags = textMap(el.path("tags"));
            String name = tags.get("name");
            if (name == null || name.isBlank()) continue;
            JsonNode point = el.has("lat") ? el : el.path("center");
            if (!point.has("lat") || !point.has("lon")) continue;
            String sourceId = el.path("type").asText() + "/" + el.path("id").asText();
            out.add(toPlace(sourceId, name, point.path("lat").asDouble(), point.path("lon").asDouble(), tags,
                    OsmTags.address(tags)));
        }
        return out;
    }

    static List<PlaceData> parseNominatim(JsonNode body) {
        List<PlaceData> out = new ArrayList<>();
        if (body == null || !body.isArray()) return out;
        for (JsonNode n : body) {
            String osmType = n.path("osm_type").asText("");
            if (osmType.isEmpty() || !n.has("osm_id")) continue;
            Map<String, String> tags = textMap(n.path("extratags"));
            String category = n.path("category").asText(n.path("class").asText(""));
            String type = n.path("type").asText("");
            if (!category.isEmpty() && !type.isEmpty()) tags.putIfAbsent(category, type);
            String display = n.path("display_name").asText("");
            String[] parts = display.split(",\\s*");
            String name = n.path("name").asText("");
            if (name.isBlank()) name = parts[0];
            if (name.isBlank()) continue;
            tags.putIfAbsent("name", name);
            String address = OsmTags.address(tags);
            if (address == null && parts.length > 1) {
                address = String.join(", ", Arrays.copyOfRange(parts, 1, Math.min(parts.length, 4)));
            }
            out.add(toPlace(osmType + "/" + n.path("osm_id").asText(), name, n.path("lat").asDouble(),
                    n.path("lon").asDouble(), tags, address));
        }
        return out;
    }

    private static PlaceData toPlace(String sourceId, String name, double lat, double lng, Map<String, String> tags,
            String address) {
        return new PlaceData(SOURCE, sourceId, truncate(name.strip(), 300), OsmTags.categoryOf(tags), lat, lng,
                address == null ? null : truncate(address, 500), OsmTags.website(tags), tags,
                OsmTags.popularity(tags));
    }

    private static Map<String, String> textMap(JsonNode node) {
        Map<String, String> map = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.properties().forEach(e -> {
                if (e.getValue().isValueNode()) map.put(e.getKey(), e.getValue().asText());
            });
        }
        return map;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
