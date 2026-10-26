package com.meridian.itinerary;

import com.meridian.geo.GeoMath;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The deterministic day plan used when AI is off or fails (spec §4: rule-based day plan): a nearest-neighbour route
 * through the sights and activities, with food stops slotted in nearest lunch and then dinner.
 */
public final class RulePlanner {

    static final int LUNCH = 13 * 60;
    static final int DINNER = 19 * 60 + 30;

    public record RuleDay(List<Stop> order, List<Stop> dropped) {}

    private RulePlanner() {}

    /** Orders the day, dropping the lowest group-fit stops until it ends in time. */
    public static RuleDay plan(List<Stop> stops, Pace pace, double fromLat, double fromLng) {
        List<Stop> remaining = new ArrayList<>(stops);
        List<Stop> dropped = new ArrayList<>();
        while (true) {
            List<Stop> order = order(remaining, pace, fromLat, fromLng);
            if (order.size() <= 1 || Scheduler.schedule(order, Map.of(), pace).fits()) {
                return new RuleDay(order, List.copyOf(dropped));
            }
            Stop worst = remaining.stream().min(Comparator.comparing((Stop s) -> s.fit() == null ? 50 : s.fit())
                    .thenComparing(Stop::title, Comparator.reverseOrder())).orElseThrow();
            remaining.remove(worst);
            dropped.add(worst);
        }
    }

    static List<Stop> order(List<Stop> stops, Pace pace, double fromLat, double fromLng) {
        List<Stop> route = nearestNeighbour(stops.stream().filter(s -> !s.isMeal()).toList(), fromLat, fromLng);
        List<Stop> meals = stops.stream().filter(Stop::isMeal)
                .sorted(Comparator.comparing((Stop s) -> s.fit() == null ? 50 : s.fit(), Comparator.reverseOrder())
                        .thenComparing(Stop::title))
                .toList();
        for (int i = 0; i < meals.size(); i++) {
            Integer target = i == 0 ? Integer.valueOf(LUNCH) : i == 1 ? Integer.valueOf(DINNER) : null;
            route = insertBest(route, meals.get(i), target, pace);
        }
        return route;
    }

    /** Where the meal lands closest to its meal time, with a light penalty for extra travel. */
    private static List<Stop> insertBest(List<Stop> route, Stop meal, Integer target, Pace pace) {
        List<Stop> best = null;
        double bestCost = Double.MAX_VALUE;
        for (int p = 0; p <= route.size(); p++) {
            List<Stop> candidate = new ArrayList<>(route);
            candidate.add(p, meal);
            Scheduler.ScheduledDay day = Scheduler.schedule(candidate, Map.of(), pace);
            int travel = day.slots().stream().mapToInt(Scheduler.Slot::travelMin).sum();
            double cost = travel * 0.5;
            if (target != null) cost += Math.abs(Scheduler.minutes(day.slots().get(p).start()) - target);
            if (cost < bestCost) {
                bestCost = cost;
                best = candidate;
            }
        }
        return best;
    }

    private static List<Stop> nearestNeighbour(List<Stop> stops, double fromLat, double fromLng) {
        List<Stop> left = new ArrayList<>(stops);
        List<Stop> route = new ArrayList<>();
        double lat = fromLat;
        double lng = fromLng;
        while (!left.isEmpty()) {
            double la = lat;
            double ln = lng;
            Stop next = left.stream().min(Comparator.comparingDouble(
                    (Stop s) -> GeoMath.distanceKm(la, ln, s.lat(), s.lng())).thenComparing(Stop::id)).orElseThrow();
            route.add(next);
            left.remove(next);
            lat = next.lat();
            lng = next.lng();
        }
        return route;
    }
}
