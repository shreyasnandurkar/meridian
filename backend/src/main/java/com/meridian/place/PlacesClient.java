package com.meridian.place;

import java.util.List;
import java.util.Map;

/** Source of real places near a destination. The OpenStreetMap implementation is {@link OsmPlacesClient}. */
public interface PlacesClient {

    /** A place as the provider describes it, before it is stored. */
    record PlaceData(
            String source,
            String sourceId,
            String name,
            PlaceCategory category,
            double lat,
            double lng,
            String address,
            String website,
            Map<String, String> tags,
            int popularityProxy) {}

    /** Named places of one category within {@code radiusKm} of a point, nearest first, at most {@code limit}. */
    List<PlaceData> nearby(double lat, double lng, PlaceCategory category, int radiusKm, int limit);

    /** Free-text search, limited to the area around a point when one is given. */
    List<PlaceData> search(String query, Double lat, Double lng, int radiusKm, int limit);

    /** Thrown when the provider cannot be reached or answers with an error. */
    class PlacesUnavailableException extends RuntimeException {
        public PlacesUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
