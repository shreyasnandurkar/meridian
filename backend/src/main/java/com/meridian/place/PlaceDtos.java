package com.meridian.place;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlaceDtos {

    private PlaceDtos() {}

    public record PlaceView(
            UUID id,
            String source,
            String sourceId,
            String sourceUrl,
            String name,
            PlaceCategory category,
            double lat,
            double lng,
            String address,
            String website,
            Map<String, String> tags,
            int popularityProxy,
            Instant fetchedAt) {

        public static PlaceView of(Place p) {
            return new PlaceView(p.getId(), p.getSource(), p.getSourceId(), sourceUrl(p), p.getName(),
                    p.getCategory(), p.getLat(), p.getLng(), p.getAddress(), p.getWebsite(), p.getTags(),
                    p.getPopularityProxy(), p.getFetchedAt());
        }

        private static String sourceUrl(Place p) {
            return OsmPlacesClient.SOURCE.equals(p.getSource())
                    ? "https://www.openstreetmap.org/" + p.getSourceId()
                    : null;
        }
    }

    /**
     * Places plus where they came from and when they were checked (spec §1 design rules). {@code stale} means the
     * provider could not be reached and these are older stored results.
     */
    public record PlaceResults(List<PlaceView> places, String source, Instant fetchedAt, boolean stale) {}
}
