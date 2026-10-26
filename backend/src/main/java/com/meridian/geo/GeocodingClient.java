package com.meridian.geo;

import java.util.List;

/** Destination lookup for trip creation. */
public interface GeocodingClient {

    record GeoResult(String name, String displayName, double lat, double lng, String type) {}

    List<GeoResult> search(String query);
}
