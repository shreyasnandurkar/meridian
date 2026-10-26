package com.meridian.itinerary;

import com.meridian.ai.job.JobContext;
import com.meridian.trip.Trip;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * A4 end to end: cluster the shortlist into days by distance (code), then plan each day (model, one call per day,
 * or rules). Returns a draft for {@link ItineraryStore} to save; does no database writes itself.
 */
@Component
public class ItineraryGenerator {

    /** Longest trip we plan day by day. */
    static final int MAX_DAYS = 14;

    public record DayDraft(int index, LocalDate date, DayPlanner.PlannedDay plan) {}

    public record Draft(Pace pace, List<DayDraft> days, List<Itinerary.Unplaced> unplaced) {}

    private final DayPlanner planner;

    public ItineraryGenerator(DayPlanner planner) {
        this.planner = planner;
    }

    public static int dayCount(Trip trip) {
        long days = ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
        return (int) Math.max(1, Math.min(MAX_DAYS, days));
    }

    public Draft generate(Trip trip, Pace pace, StopFactory.TripStops stops, boolean regenerate, JobContext job) {
        int dayCount = dayCount(trip);
        List<Itinerary.Unplaced> unplaced = new ArrayList<>(stops.unplaceable());
        job.progress(5, "Grouping " + stops.stops().size() + " places into " + dayCount + " days by distance");
        DayClusterer.Clustering clustering = DayClusterer.cluster(List.copyOf(stops.stops().values()), dayCount,
                pace.maxStops());
        clustering.dropped().forEach(s -> unplaced.add(new Itinerary.Unplaced(s.id(), "Didn't fit in " + dayCount
                + " days at a " + pace.label() + " pace; it has one of the lowest group-fit scores.")));
        double[] from = origin(trip, stops);
        List<DayDraft> days = new ArrayList<>();
        for (int i = 0; i < dayCount; i++) {
            int index = i + 1;
            List<Stop> dayStops = clustering.days().get(i);
            if (!dayStops.isEmpty()) {
                job.progress(10 + 85 * i / dayCount, (planner.aiEnabled() ? "Planning" : "Ordering") + " day "
                        + index + " of " + dayCount);
            }
            DayPlanner.PlannedDay plan = planner.plan(new DayPlanner.DayContext(index, dayCount,
                    trip.getStartDate().plusDays(i), pace, from[0], from[1]), dayStops, regenerate);
            plan.dropped().forEach(s -> unplaced.add(new Itinerary.Unplaced(s.id(), "Didn't fit in day " + index
                    + " at a " + pace.label() + " pace.")));
            days.add(new DayDraft(index, trip.getStartDate().plusDays(i), plan));
        }
        return new Draft(pace, days, unplaced);
    }

    /** Where days start from: the destination, else the middle of the shortlist. */
    static double[] origin(Trip trip, StopFactory.TripStops stops) {
        if (trip.getDestinationLat() != null && trip.getDestinationLng() != null) {
            return new double[] {trip.getDestinationLat(), trip.getDestinationLng()};
        }
        return new double[] {
            stops.stops().values().stream().mapToDouble(Stop::lat).average().orElse(0),
            stops.stops().values().stream().mapToDouble(Stop::lng).average().orElse(0)};
    }
}
