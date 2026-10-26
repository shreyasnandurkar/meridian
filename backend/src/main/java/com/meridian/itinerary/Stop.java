package com.meridian.itinerary;

import com.meridian.geo.GeoMath;
import com.meridian.place.PlaceCategory;
import java.util.UUID;

/**
 * A shortlisted place that can go on a day: where it is, how long a visit takes, its group-fit score (used to decide
 * what to leave out when there are too many) and a short "what it offers" for the prompt.
 */
public record Stop(UUID id, String title, PlaceCategory category, double lat, double lng, int durationMin,
        Integer fit, String offers) {

    public boolean isMeal() {
        return category == PlaceCategory.FOOD;
    }

    /** Typical visit length by category, in minutes. */
    public static int durationFor(PlaceCategory category) {
        return switch (category) {
            case SIGHTS -> 90;
            case FOOD -> 75;
            case ACTIVITIES -> 150;
            default -> 60;
        };
    }

    /** Getting from one stop to the next. */
    public record Leg(int minutes, double km) {}

    /**
     * Rough door-to-door time: walking up to 1.2 km, otherwise a taxi or scooter at 24 km/h on roads 35% longer than
     * the straight line, plus 10 minutes to find a ride and park. Rounded up to 5 minutes.
     */
    public static Leg leg(Stop from, Stop to) {
        double km = GeoMath.distanceKm(from.lat(), from.lng(), to.lat(), to.lng());
        double minutes = km <= 1.2 ? km * 1.25 / 4.5 * 60 + 3 : km * 1.35 / 24 * 60 + 10;
        int rounded = (int) Math.ceil(Math.max(minutes, 5) / 5.0) * 5;
        return new Leg(rounded, Math.round(km * 10) / 10.0);
    }
}
