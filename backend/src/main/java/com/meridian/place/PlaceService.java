package com.meridian.place;

import com.meridian.common.ApiException;
import com.meridian.config.MeridianProperties;
import com.meridian.geo.GeoMath;
import com.meridian.place.PlaceDtos.PlaceResults;
import com.meridian.place.PlaceDtos.PlaceView;
import com.meridian.place.PlacesClient.PlaceData;
import com.meridian.place.PlacesClient.PlacesUnavailableException;
import com.meridian.trip.Trip;
import com.meridian.trip.TripAccess;
import com.meridian.trip.TripRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Place lookup around a trip's destination. Provider answers are stored in {@code places} and remembered per query in
 * {@code place_search_cache}; repeated queries within the TTL never reach the provider, and when the provider is down
 * older stored answers are served and marked stale. Provider calls run outside any database transaction.
 */
@Service
public class PlaceService {

    static final String SOURCE_NAME = "OpenStreetMap";
    static final int NEARBY_LIMIT = 60;
    static final int SEARCH_LIMIT = 10;
    static final int MAX_RADIUS_KM = 80;

    private final PlacesClient client;
    private final PlaceRepository places;
    private final PlaceSearchCacheRepository cache;
    private final TripRepository trips;
    private final TripAccess access;
    private final TransactionTemplate tx;
    private final int defaultRadiusKm;
    private final Duration ttl;

    public PlaceService(PlacesClient client, PlaceRepository places, PlaceSearchCacheRepository cache,
            TripRepository trips, TripAccess access, PlatformTransactionManager txManager, MeridianProperties props) {
        this.client = client;
        this.places = places;
        this.cache = cache;
        this.trips = trips;
        this.access = access;
        this.tx = new TransactionTemplate(txManager);
        this.defaultRadiusKm = props.places().radiusKm();
        this.ttl = props.places().cacheTtl();
    }

    public PlaceResults nearby(UUID tripId, UUID userId, PlaceCategory category, Integer radiusKm) {
        if (category == PlaceCategory.OTHER) throw ApiException.badRequest("Pick a category to browse");
        Trip trip = memberTrip(tripId, userId);
        if (trip.getDestinationLat() == null || trip.getDestinationLng() == null) {
            throw ApiException.conflict("This trip has no map location. Search by name, paste a link or drop a pin.");
        }
        double lat = trip.getDestinationLat();
        double lng = trip.getDestinationLng();
        int radius = radiusKm == null ? defaultRadiusKm : Math.clamp(radiusKm, 1, MAX_RADIUS_KM);
        String key = String.format(Locale.ROOT, "nearby:%s:%.3f,%.3f:%d", category, lat, lng, radius);
        return lookup(key, () -> client.nearby(lat, lng, category, radius, NEARBY_LIMIT), () -> {
            // no cached answer for this exact query: anything stored inside the radius is better than nothing
            double dLat = radius / 111.32;
            double dLng = radius / (111.32 * Math.max(0.1, Math.cos(Math.toRadians(lat))));
            return places.findByCategoryAndLatBetweenAndLngBetween(category, lat - dLat, lat + dLat, lng - dLng,
                            lng + dLng).stream()
                    .filter(p -> GeoMath.distanceKm(lat, lng, p.getLat(), p.getLng()) <= radius)
                    .sorted(Comparator.comparingDouble(p -> GeoMath.distanceKm(lat, lng, p.getLat(), p.getLng())))
                    .limit(NEARBY_LIMIT)
                    .toList();
        });
    }

    public PlaceResults search(UUID tripId, UUID userId, String query) {
        Trip trip = memberTrip(tripId, userId);
        String q = query.strip().replaceAll("\\s+", " ");
        if (q.length() < 2) return new PlaceResults(List.of(), SOURCE_NAME, null, false);
        Double lat = trip.getDestinationLat();
        Double lng = trip.getDestinationLng();
        boolean located = lat != null && lng != null;
        // without coordinates, name the destination in the query instead of bounding the search
        String providerQuery = located ? q : q + ", " + trip.getDestinationName();
        String area = located ? String.format(Locale.ROOT, "%.3f,%.3f", lat, lng)
                : trip.getDestinationName().toLowerCase(Locale.ROOT);
        String key = "search:" + q.toLowerCase(Locale.ROOT) + ":" + area;
        return lookup(key, () -> client.search(providerQuery, lat, lng, defaultRadiusKm, SEARCH_LIMIT), List::of);
    }

    /** Stored place for a shortlist item; must exist. */
    public Place require(UUID placeId) {
        return places.findById(placeId).orElseThrow(() -> ApiException.badRequest("Unknown place"));
    }

    private Trip memberTrip(UUID tripId, UUID userId) {
        access.requireMember(tripId, userId);
        return trips.findById(tripId).orElseThrow(() -> ApiException.notFound("Trip not found"));
    }

    private PlaceResults lookup(String key, Supplier<List<PlaceData>> fetch, Supplier<List<Place>> lastResort) {
        if (key.length() > 400) key = key.substring(0, 400);
        PlaceSearchCacheEntry cached = cache.findById(key).orElse(null);
        if (cached != null && cached.getFetchedAt().isAfter(Instant.now().minus(ttl))) {
            return results(cached, false);
        }
        List<PlaceData> fresh;
        try {
            fresh = fetch.get();
        } catch (PlacesUnavailableException e) {
            if (cached != null) return results(cached, true);
            List<Place> stored = lastResort.get();
            if (!stored.isEmpty()) {
                Instant oldest = stored.stream().map(Place::getFetchedAt).min(Comparator.naturalOrder()).orElseThrow();
                return new PlaceResults(stored.stream().map(PlaceView::of).toList(), SOURCE_NAME, oldest, true);
            }
            throw ApiException.badGateway(
                    "OpenStreetMap place search is unavailable right now. Paste a link or drop a pin instead.");
        }
        String cacheKey = key;
        return tx.execute(status -> {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            List<Place> saved = upsert(fresh, now);
            PlaceSearchCacheEntry entry = cache.findById(cacheKey).orElseGet(() -> new PlaceSearchCacheEntry(cacheKey));
            entry.update(saved.stream().map(Place::getId).toList(), now);
            cache.save(entry);
            return new PlaceResults(saved.stream().map(PlaceView::of).toList(), SOURCE_NAME, now, false);
        });
    }

    private List<Place> upsert(List<PlaceData> data, Instant now) {
        if (data.isEmpty()) return List.of();
        Map<String, List<PlaceData>> bySource = data.stream().collect(Collectors.groupingBy(PlaceData::source));
        Map<String, Place> existing = bySource.entrySet().stream()
                .flatMap(e -> places.findBySourceAndSourceIdIn(e.getKey(),
                        e.getValue().stream().map(PlaceData::sourceId).toList()).stream())
                .collect(Collectors.toMap(p -> p.getSource() + ":" + p.getSourceId(), Function.identity()));
        return data.stream()
                .collect(Collectors.toMap(d -> d.source() + ":" + d.sourceId(), Function.identity(), (a, b) -> a,
                        LinkedHashMap::new))
                .entrySet().stream()
                .map(e -> {
                    Place place = existing.get(e.getKey());
                    if (place == null) return places.save(new Place(e.getValue(), now));
                    place.refresh(e.getValue(), now);
                    return place;
                })
                .toList();
    }

    private PlaceResults results(PlaceSearchCacheEntry entry, boolean stale) {
        Map<UUID, Place> byId = places.findAllById(entry.getPlaceIds()).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        List<PlaceView> views = entry.getPlaceIds().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(PlaceView::of)
                .toList();
        return new PlaceResults(views, SOURCE_NAME, entry.getFetchedAt(), stale);
    }
}
