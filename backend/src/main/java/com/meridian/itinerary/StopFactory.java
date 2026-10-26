package com.meridian.itinerary;

import com.meridian.fit.GroupFitService;
import com.meridian.fit.PlaceSignals;
import com.meridian.place.Place;
import com.meridian.place.PlaceCategory;
import com.meridian.place.PlaceRepository;
import com.meridian.profile.Vocabulary;
import com.meridian.shortlist.ShortlistItem;
import com.meridian.shortlist.ShortlistItemRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Turns a trip's shortlist into schedulable {@link Stop}s, and says why the rest can't go on a day. */
@Component
public class StopFactory {

    static final String STAY_REASON = "A place to stay, not a day stop.";
    static final String NO_LOCATION = "No location yet: add it as a place or a map pin to schedule it.";

    /**
     * @param stops       shortlist item id → stop, for items with a location that are not stays
     * @param unplaceable shortlist items that can't go on a day, with the reason
     * @param items       every shortlist item, by id
     * @param titles      display title per shortlist item
     */
    public record TripStops(Map<UUID, Stop> stops, List<Itinerary.Unplaced> unplaceable,
            Map<UUID, ShortlistItem> items, Map<UUID, String> titles) {}

    private final ShortlistItemRepository shortlist;
    private final PlaceRepository places;
    private final GroupFitService groupFit;

    public StopFactory(ShortlistItemRepository shortlist, PlaceRepository places, GroupFitService groupFit) {
        this.shortlist = shortlist;
        this.places = places;
        this.groupFit = groupFit;
    }

    /** {@code withFit} adds group-fit scores, used to decide what to leave out when there is too much. */
    @Transactional(readOnly = true)
    public TripStops load(UUID tripId, boolean withFit) {
        List<ShortlistItem> list = shortlist.findByTripIdOrderByCreatedAtDesc(tripId);
        Map<UUID, Place> byPlace = places.findAllById(list.stream().map(ShortlistItem::getPlaceId)
                        .filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        Map<UUID, Integer> fit = withFit && !list.isEmpty() ? groupFit.scores(tripId) : Map.of();
        Map<UUID, Stop> stops = new LinkedHashMap<>();
        List<Itinerary.Unplaced> unplaceable = new ArrayList<>();
        Map<UUID, ShortlistItem> items = new LinkedHashMap<>();
        Map<UUID, String> titles = new LinkedHashMap<>();
        // oldest first, so ties in clustering follow the order things were added
        for (ShortlistItem item : list.reversed()) {
            Place place = item.getPlaceId() == null ? null : byPlace.get(item.getPlaceId());
            String title = place != null ? place.getName() : item.getTitle();
            items.put(item.getId(), item);
            titles.put(item.getId(), title);
            Double lat = place != null ? Double.valueOf(place.getLat()) : item.getLat();
            Double lng = place != null ? Double.valueOf(place.getLng()) : item.getLng();
            if (item.getCategory() == PlaceCategory.STAYS) {
                unplaceable.add(new Itinerary.Unplaced(item.getId(), STAY_REASON));
            } else if (lat == null || lng == null) {
                unplaceable.add(new Itinerary.Unplaced(item.getId(), NO_LOCATION));
            } else {
                PlaceSignals signals = place != null ? PlaceSignals.of(place)
                        : PlaceSignals.titled(title, item.getCategory());
                stops.put(item.getId(), new Stop(item.getId(), title, item.getCategory(), lat, lng,
                        Stop.durationFor(item.getCategory()), fit.get(item.getId()), offers(signals)));
            }
        }
        return new TripStops(stops, unplaceable, items, titles);
    }

    /** The place's three strongest interests in words, e.g. "history, architecture". */
    static String offers(PlaceSignals signals) {
        Map<String, String> labels = Vocabulary.interestLabels();
        String text = signals.interests().entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(3)
                .map(e -> labels.getOrDefault(e.getKey(), e.getKey()).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
        return text.isEmpty() ? signals.category().name().toLowerCase(Locale.ROOT) : text;
    }
}
