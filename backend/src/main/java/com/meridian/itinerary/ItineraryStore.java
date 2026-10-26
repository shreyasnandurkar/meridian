package com.meridian.itinerary;

import com.meridian.common.ApiException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Every write to an itinerary, each in one transaction: saving a generated draft, a re-planned day, or an edit. */
@Component
public class ItineraryStore {

    /** Stands in for "the start of the day" as the predecessor of each day's first stop. */
    private static final UUID DAY_START = new UUID(0, 0);

    private final ItineraryRepository itineraries;
    private final ItineraryDayRepository days;
    private final ItineraryItemRepository items;
    private final ProposalRepository proposals;
    private final StopFactory stopFactory;

    public ItineraryStore(ItineraryRepository itineraries, ItineraryDayRepository days, ItineraryItemRepository items,
            ProposalRepository proposals, StopFactory stopFactory) {
        this.itineraries = itineraries;
        this.days = days;
        this.items = items;
        this.proposals = proposals;
        this.stopFactory = stopFactory;
    }

    /** Replaces the trip's itinerary with a fresh draft. Open proposals were made against the old plan: stale. */
    @Transactional
    public Itinerary saveDraft(UUID tripId, UUID userId, ItineraryGenerator.Draft draft) {
        Itinerary itinerary = itineraries.findByTripId(tripId).orElse(null);
        if (itinerary == null) {
            itinerary = itineraries.save(new Itinerary(tripId, draft.pace(), userId));
            itinerary.setUnplaced(draft.unplaced());
        } else {
            requireDraft(itinerary);
            items.deleteByItineraryId(itinerary.getId());
            days.deleteByItineraryId(itinerary.getId());
            // deletes must reach the database before the new rows reuse their unique keys
            items.flush();
            itinerary.regenerated(draft.pace(), userId, draft.unplaced());
            for (Proposal p : proposals.findByItineraryIdAndStatus(itinerary.getId(), Proposal.Status.OPEN)) {
                p.resolve(Proposal.Status.STALE);
            }
        }
        for (ItineraryGenerator.DayDraft d : draft.days()) {
            ItineraryDay day = new ItineraryDay(itinerary.getId(), d.index(), d.date());
            DayPlanner.PlannedDay plan = d.plan();
            day.planned(plan.summary(), plan.ai(), plan.model(), plan.promptVersion(), plan.repairs());
            days.save(day);
            saveSlots(itinerary.getId(), day.getId(), plan.slots(), plan.notes());
        }
        return itinerary;
    }

    /** Replaces one day's plan (same stops, new order and times); stops the new plan dropped become unplaced. */
    @Transactional
    public void saveDay(UUID tripId, int dayIndex, DayPlanner.PlannedDay plan) {
        Itinerary itinerary = require(tripId);
        requireDraft(itinerary);
        ItineraryDay day = days.findByItineraryIdAndDayIndex(itinerary.getId(), dayIndex)
                .orElseThrow(() -> ApiException.notFound("There is no day " + dayIndex));
        items.deleteByDayId(day.getId());
        items.flush();
        day.planned(plan.summary(), plan.ai(), plan.model(), plan.promptVersion(), plan.repairs());
        saveSlots(itinerary.getId(), day.getId(), plan.slots(), plan.notes());
        if (!plan.dropped().isEmpty()) {
            List<Itinerary.Unplaced> unplaced = new ArrayList<>(itinerary.getUnplaced());
            plan.dropped().forEach(s -> unplaced.add(new Itinerary.Unplaced(s.id(), "Didn't fit in day " + dayIndex
                    + " when it was re-planned.")));
            itinerary.setUnplaced(unplaced);
        }
        itinerary.changed();
    }

    /**
     * Applies an edit to the plan as it is now and reschedules the days it touched. Stops that stay on their day keep
     * their time when they still can; code recomputes the rest. {@code handNote} goes in those days' "Why?" list.
     */
    @Transactional
    public void applyEdit(UUID tripId, PlanEdit edit, String handNote) {
        Itinerary itinerary = require(tripId);
        requireDraft(itinerary);
        List<ItineraryDay> dayList = days.findByItineraryIdOrderByDayIndex(itinerary.getId());
        List<ItineraryItem> all = items.findByItineraryIdOrderByPosition(itinerary.getId());
        Layout layout = layoutOf(dayList, all);
        StopFactory.TripStops stops = stopFactory.load(tripId, false);
        List<String> problems = layout.check(edit, stops.stops().keySet());
        if (!problems.isEmpty()) throw ApiException.conflict(String.join(" ", problems));

        Map<UUID, ItineraryItem> current = new HashMap<>();
        all.forEach(i -> current.put(i.getShortlistItemId(), i));
        Map<UUID, Integer> dayOf = new HashMap<>();
        dayList.forEach(d -> all.stream().filter(i -> i.getDayId().equals(d.getId()))
                .forEach(i -> dayOf.put(i.getShortlistItemId(), d.getDayIndex())));

        // what came before each stop, so a stop keeps its time only while its neighbour stays the same
        Map<UUID, UUID> before = previousStops(layout);
        Set<Integer> changed = layout.apply(edit);
        Map<UUID, UUID> after = previousStops(layout);
        for (int index : changed) items.deleteByDayId(dayList.get(index - 1).getId());
        items.flush();
        for (int index : changed) {
            ItineraryDay day = dayList.get(index - 1);
            List<Stop> order = layout.day(index).stream().map(stops.stops()::get).filter(Objects::nonNull).toList();
            Map<UUID, LocalTime> keep = new HashMap<>();
            Map<UUID, String> notes = new HashMap<>();
            for (Stop s : order) {
                ItineraryItem was = current.get(s.id());
                if (was == null) continue;
                boolean sameDay = Objects.equals(dayOf.get(s.id()), index);
                if (sameDay && Objects.equals(before.get(s.id()), after.get(s.id()))) {
                    keep.put(s.id(), was.getStartTime());
                }
                if (was.getNote() != null) notes.put(s.id(), was.getNote());
            }
            saveSlots(itinerary.getId(), day.getId(), Scheduler.schedule(order, keep, itinerary.getPace()).slots(),
                    notes);
            day.editedByHand(handNote, DayPlanner.templateSummary(order));
        }
        List<Itinerary.Unplaced> unplaced = new ArrayList<>(itinerary.getUnplaced().stream()
                .filter(u -> !u.shortlistItemId().equals(edit.itemId())).toList());
        if (edit.type() == PlanEdit.Type.REMOVE) {
            unplaced.add(new Itinerary.Unplaced(edit.itemId(), "Taken off day " + dayOf.get(edit.itemId()) + "."));
        }
        itinerary.setUnplaced(unplaced);
        itinerary.changed();
    }

    /** What stops {@code edit} from applying to the plan as it is now; empty when it can be applied. */
    @Transactional(readOnly = true)
    public List<String> check(UUID tripId, PlanEdit edit) {
        Itinerary itinerary = require(tripId);
        Layout layout = layoutOf(days.findByItineraryIdOrderByDayIndex(itinerary.getId()),
                items.findByItineraryIdOrderByPosition(itinerary.getId()));
        return layout.check(edit, stopFactory.load(tripId, false).stops().keySet());
    }

    /** Each stop's predecessor on its day; the first stop of a day maps to a fixed marker. */
    private static Map<UUID, UUID> previousStops(Layout layout) {
        Map<UUID, UUID> out = new HashMap<>();
        for (int d = 1; d <= layout.dayCount(); d++) {
            UUID prev = DAY_START;
            for (UUID id : layout.day(d)) {
                out.put(id, prev);
                prev = id;
            }
        }
        return out;
    }

    /** The plan as it is now, for checking edits and proposals. */
    static Layout layoutOf(List<ItineraryDay> dayList, List<ItineraryItem> all) {
        List<List<UUID>> out = new ArrayList<>();
        for (ItineraryDay d : dayList) {
            out.add(all.stream().filter(i -> i.getDayId().equals(d.getId())).map(ItineraryItem::getShortlistItemId)
                    .toList());
        }
        return Layout.of(out);
    }

    private void saveSlots(UUID itineraryId, UUID dayId, List<Scheduler.Slot> slots, Map<UUID, String> notes) {
        for (int p = 0; p < slots.size(); p++) {
            Scheduler.Slot slot = slots.get(p);
            items.save(new ItineraryItem(itineraryId, dayId, slot.stop().id(), p, slot, notes.get(slot.stop().id())));
        }
    }

    private Itinerary require(UUID tripId) {
        return itineraries.findByTripId(tripId)
                .orElseThrow(() -> ApiException.notFound("This trip has no itinerary yet"));
    }

    public static void requireDraft(Itinerary itinerary) {
        if (itinerary.isLocked()) throw ApiException.conflict("The itinerary is locked; unlock it to make changes");
    }
}
