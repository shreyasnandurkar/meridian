package com.meridian.itinerary;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;

/**
 * Which stop is on which day, in order — the part of an itinerary that edits and proposals change. Pure logic: the
 * service loads it from the database, applies a {@link PlanEdit} and reschedules the days that changed.
 */
public final class Layout {

    /** Where a stop is: 1-based day, 0-based position. */
    public record Spot(int day, int position) {}

    private final List<List<UUID>> days;

    private Layout(List<List<UUID>> days) {
        this.days = days;
    }

    /** {@code days.get(0)} is day 1. */
    public static Layout of(List<List<UUID>> days) {
        List<List<UUID>> copy = new ArrayList<>();
        days.forEach(d -> copy.add(new ArrayList<>(d)));
        return new Layout(copy);
    }

    public List<UUID> day(int index) {
        return List.copyOf(days.get(index - 1));
    }

    public int dayCount() {
        return days.size();
    }

    public Spot find(UUID id) {
        for (int d = 0; d < days.size(); d++) {
            int p = days.get(d).indexOf(id);
            if (p >= 0) return new Spot(d + 1, p);
        }
        return null;
    }

    /**
     * What stops the edit from applying to the plan as it is now; empty when it can be applied.
     *
     * @param shortlisted ids of the trip's shortlist items that can go on a day (they have a location)
     */
    public List<String> check(PlanEdit e, Set<UUID> shortlisted) {
        List<String> problems = new ArrayList<>();
        Spot at = find(e.itemId());
        switch (e.type()) {
            case MOVE -> {
                if (at == null) problems.add("That place is not on the itinerary any more.");
                checkDay(e, problems);
            }
            case SWAP -> {
                if (at == null) problems.add("That place is not on the itinerary any more.");
                if (e.otherItemId() == null) problems.add("Choose the place to swap with.");
                else if (find(e.otherItemId()) == null) problems.add("The place to swap with is not on the itinerary.");
                else if (e.otherItemId().equals(e.itemId())) problems.add("Choose two different places.");
            }
            case REMOVE -> {
                if (at == null) problems.add("That place is not on the itinerary any more.");
            }
            case ADD -> {
                if (at != null) problems.add("That place is already on day " + at.day() + ".");
                else if (!shortlisted.contains(e.itemId())) {
                    problems.add("Only shortlisted places with a location can be added.");
                }
                checkDay(e, problems);
            }
        }
        return problems;
    }

    private void checkDay(PlanEdit e, List<String> problems) {
        if (e.day() == null) problems.add("Choose a day.");
        else if (e.day() < 1 || e.day() > days.size()) problems.add("There is no day " + e.day() + ".");
    }

    /** Applies an edit that passed {@link #check}; returns the days whose stops changed. */
    public Set<Integer> apply(PlanEdit e) {
        Set<Integer> changed = new TreeSet<>();
        switch (e.type()) {
            case MOVE -> {
                Spot from = find(e.itemId());
                days.get(from.day() - 1).remove(from.position());
                insert(e.itemId(), e.day(), e.position());
                changed.add(from.day());
                changed.add(e.day());
            }
            case SWAP -> {
                Spot a = find(e.itemId());
                Spot b = find(e.otherItemId());
                days.get(a.day() - 1).set(a.position(), e.otherItemId());
                days.get(b.day() - 1).set(b.position(), e.itemId());
                changed.add(a.day());
                changed.add(b.day());
            }
            case REMOVE -> {
                Spot from = find(e.itemId());
                days.get(from.day() - 1).remove(from.position());
                changed.add(from.day());
            }
            case ADD -> {
                insert(e.itemId(), e.day(), e.position());
                changed.add(e.day());
            }
        }
        return changed;
    }

    private void insert(UUID id, int day, Integer position) {
        List<UUID> list = days.get(day - 1);
        int p = position == null ? list.size() : Math.min(position, list.size());
        list.add(p, id);
    }

    /** Plain-language description, written by code so it can't misstate the change. */
    public String describe(PlanEdit e, Function<UUID, String> title) {
        Spot at = find(e.itemId());
        String name = title.apply(e.itemId());
        return switch (e.type()) {
            case MOVE -> at != null && at.day() == e.day()
                    ? "Move " + name + " to stop " + (position(e) + 1) + " on day " + e.day()
                    : "Move " + name + (at == null ? "" : " from day " + at.day()) + " to day " + e.day();
            case SWAP -> {
                Spot other = find(e.otherItemId());
                yield "Swap " + name + (at == null ? "" : " (day " + at.day() + ")") + " with "
                        + title.apply(e.otherItemId()) + (other == null ? "" : " (day " + other.day() + ")");
            }
            case REMOVE -> "Remove " + name + (at == null ? "" : " from day " + at.day());
            case ADD -> "Add " + name + " to day " + e.day();
        };
    }

    private int position(PlanEdit e) {
        int size = days.get(e.day() - 1).size();
        return e.position() == null ? size - 1 : Math.min(e.position(), size - 1);
    }
}
