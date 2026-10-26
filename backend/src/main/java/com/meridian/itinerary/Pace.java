package com.meridian.itinerary;

import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** How full a day is: when it starts and ends, and how many stops fit. */
public enum Pace {
    SLOW(LocalTime.of(10, 0), LocalTime.of(19, 0), 3),
    BALANCED(LocalTime.of(9, 30), LocalTime.of(20, 0), 4),
    PACKED(LocalTime.of(8, 30), LocalTime.of(21, 30), 6);

    private final LocalTime dayStart;
    private final LocalTime dayEnd;
    private final int maxStops;

    Pace(LocalTime dayStart, LocalTime dayEnd, int maxStops) {
        this.dayStart = dayStart;
        this.dayEnd = dayEnd;
        this.maxStops = maxStops;
    }

    public LocalTime dayStart() {
        return dayStart;
    }

    public LocalTime dayEnd() {
        return dayEnd;
    }

    public int maxStops() {
        return maxStops;
    }

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Profile paces are "slow", "balanced" or "packed"; anything else counts as balanced. */
    public static Pace fromProfile(String pace) {
        if (pace == null) return BALANCED;
        return switch (pace.toLowerCase(Locale.ROOT)) {
            case "slow" -> SLOW;
            case "packed" -> PACKED;
            default -> BALANCED;
        };
    }

    /**
     * The group's pace is the median member's, so neither extreme sets it; with an even split the middle option wins
     * (one slow and one packed traveler get balanced days). Nobody profiled means balanced.
     */
    public static Pace forGroup(Collection<String> memberPaces) {
        List<Integer> sorted = memberPaces.stream().filter(Objects::nonNull).map(p -> fromProfile(p).ordinal())
                .sorted().toList();
        if (sorted.isEmpty()) return BALANCED;
        int n = sorted.size();
        if (n % 2 == 1) return values()[sorted.get(n / 2)];
        int lo = sorted.get(n / 2 - 1);
        int hi = sorted.get(n / 2);
        return lo == hi ? values()[lo] : BALANCED;
    }
}
