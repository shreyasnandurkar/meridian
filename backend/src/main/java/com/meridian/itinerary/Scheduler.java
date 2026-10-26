package com.meridian.itinerary;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns an ordered list of stops into clock times (spec §4 A4: code checks travel feasibility). A requested start —
 * the model's, or the time a stop had before an edit — is kept when it can be reached; otherwise the stop starts as
 * soon as it can and the change is recorded, so the "Why?" popover can say what code repaired.
 */
public final class Scheduler {

    /** Latest time anything can end; keeps the arithmetic inside one day. */
    private static final int LAST_MINUTE = 23 * 60 + 55;
    /** Meals start at lunch or dinner time; a meal reached earlier waits, one reached mid-afternoon becomes dinner. */
    static final int LUNCH_FROM = 12 * 60;
    static final int LUNCH_UNTIL = 15 * 60 + 30;
    static final int DINNER_FROM = 19 * 60;

    public record Slot(Stop stop, LocalTime start, int travelMin, double travelKm) {

        public LocalTime end() {
            return start.plusMinutes(stop.durationMin());
        }
    }

    /** {@code overflowMin} is how far the last stop runs past the end of the day at this pace (0 when it fits). */
    public record ScheduledDay(List<Slot> slots, List<String> repairs, int overflowMin) {

        public boolean fits() {
            return overflowMin == 0;
        }
    }

    private Scheduler() {}

    public static ScheduledDay schedule(List<Stop> order, Map<UUID, LocalTime> requested, Pace pace) {
        List<Slot> slots = new ArrayList<>();
        List<String> repairs = new ArrayList<>();
        int dayStart = minutes(pace.dayStart());
        int free = dayStart;
        Stop previous = null;
        for (Stop stop : order) {
            Stop.Leg leg = previous == null ? new Stop.Leg(0, 0) : Stop.leg(previous, stop);
            int earliest = roundUp(free + leg.minutes());
            LocalTime wanted = requested.get(stop.id());
            int start = earliest;
            if (wanted != null) {
                int w = minutes(wanted);
                if (w >= earliest) {
                    start = roundUp(w);
                } else if (earliest - w >= 5) {
                    repairs.add(previous == null
                            ? "Started " + stop.title() + " at " + clock(earliest) + " instead of " + clock(w)
                                    + ", when the day begins"
                            : "Moved " + stop.title() + " from " + clock(w) + " to " + clock(earliest) + " to allow "
                                    + leg.minutes() + " min from " + previous.title());
                }
            }
            if (stop.isMeal()) {
                int meal = mealStart(start);
                if (meal != start && wanted != null) {
                    repairs.add("Moved " + stop.title() + " from " + clock(start) + " to " + clock(meal) + ", "
                            + (meal < DINNER_FROM ? "lunch" : "dinner") + " time");
                }
                start = meal;
            }
            start = Math.min(start, LAST_MINUTE - stop.durationMin());
            slots.add(new Slot(stop, LocalTime.of(start / 60, start % 60), leg.minutes(), leg.km()));
            free = start + stop.durationMin();
            previous = stop;
        }
        int overflow = Math.max(0, free - minutes(pace.dayEnd()));
        return new ScheduledDay(slots, repairs, slots.isEmpty() ? 0 : overflow);
    }

    static int mealStart(int start) {
        if (start < LUNCH_FROM) return LUNCH_FROM;
        if (start > LUNCH_UNTIL && start < DINNER_FROM) return DINNER_FROM;
        return start;
    }

    static int minutes(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    static String clock(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    private static int roundUp(int minutes) {
        return (int) Math.ceil(minutes / 5.0) * 5;
    }
}
