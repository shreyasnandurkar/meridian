package com.meridian.itinerary;

import com.meridian.ai.job.AiJob;
import com.meridian.ai.job.AiJobService;
import com.meridian.common.ApiException;
import com.meridian.fit.TripGroupLoader;
import com.meridian.itinerary.ItineraryDtos.GenerateRequest;
import com.meridian.itinerary.ItineraryDtos.ItineraryView;
import com.meridian.realtime.TripEvents;
import com.meridian.trip.Trip;
import com.meridian.trip.TripAccess;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The Itinerary tab (spec §5.7): generate a draft (A4, async), re-plan one day, and the organizer's direct edits.
 * Members change the plan through proposals ({@link ProposalService}).
 */
@Service
public class ItineraryService {

    static final String FEATURE = "itinerary";

    private final TripAccess access;
    private final TripGroupLoader loader;
    private final StopFactory stopFactory;
    private final ItineraryGenerator generator;
    private final DayPlanner planner;
    private final ItineraryStore store;
    private final ItineraryViews views;
    private final ItineraryRepository itineraries;
    private final ItineraryDayRepository days;
    private final ItineraryItemRepository items;
    private final UserRepository users;
    private final AiJobService jobs;
    private final TripEvents events;

    public ItineraryService(TripAccess access, TripGroupLoader loader, StopFactory stopFactory,
            ItineraryGenerator generator, DayPlanner planner, ItineraryStore store, ItineraryViews views,
            ItineraryRepository itineraries, ItineraryDayRepository days, ItineraryItemRepository items,
            UserRepository users, AiJobService jobs, TripEvents events) {
        this.access = access;
        this.loader = loader;
        this.stopFactory = stopFactory;
        this.generator = generator;
        this.planner = planner;
        this.store = store;
        this.views = views;
        this.itineraries = itineraries;
        this.days = days;
        this.items = items;
        this.users = users;
        this.jobs = jobs;
        this.events = events;
    }

    public Optional<ItineraryView> get(UUID tripId, UUID userId) {
        access.requireMember(tripId, userId);
        return views.build(tripId, userId, groupPace(tripId));
    }

    /** Organizer only. Replaces any existing draft; runs as an AI job with a progress message per day. */
    public AiJob generate(UUID tripId, UUID userId, GenerateRequest req) {
        access.requireOrganizer(tripId, userId);
        itineraries.findByTripId(tripId).ifPresent(ItineraryStore::requireDraft);
        TripGroupLoader.TripGroup group = loader.load(tripId);
        Trip trip = group.trip();
        Pace pace = req != null && req.pace() != null ? req.pace() : groupPace(group);
        StopFactory.TripStops stops = stopFactory.load(tripId, true);
        if (stops.stops().isEmpty()) {
            throw ApiException.badRequest("Shortlist a few places with a location first; links without one and "
                    + "stays can't be scheduled.");
        }
        return jobs.submit(userId, tripId, FEATURE, ctx -> {
            ItineraryGenerator.Draft draft = generator.generate(trip, pace, stops, false, ctx);
            ctx.progress(97, "Saving the draft");
            store.saveDraft(tripId, userId, draft);
            events.publish(tripId, TripEvents.Type.ITINERARY, userId);
            events.publish(tripId, TripEvents.Type.PROPOSALS, userId);
            events.publish(tripId, TripEvents.Type.APPROVAL, userId);
            ctx.progress(100, "Draft itinerary ready");
            return views.build(tripId, userId, groupPace(group)).orElseThrow();
        });
    }

    /** Organizer only: ask for a fresh order and times for one day's stops (skips the cache). */
    public AiJob regenerateDay(UUID tripId, UUID userId, int dayIndex) {
        access.requireOrganizer(tripId, userId);
        Itinerary itinerary = itineraries.findByTripId(tripId)
                .orElseThrow(() -> ApiException.notFound("This trip has no itinerary yet"));
        ItineraryStore.requireDraft(itinerary);
        ItineraryDay day = days.findByItineraryIdAndDayIndex(itinerary.getId(), dayIndex)
                .orElseThrow(() -> ApiException.notFound("There is no day " + dayIndex));
        TripGroupLoader.TripGroup group = loader.load(tripId);
        StopFactory.TripStops stops = stopFactory.load(tripId, true);
        List<Stop> dayStops = items.findByDayIdOrderByPosition(day.getId()).stream()
                .map(i -> stops.stops().get(i.getShortlistItemId())).filter(Objects::nonNull).toList();
        if (dayStops.isEmpty()) throw ApiException.badRequest("Day " + dayIndex + " has no stops to plan");
        double[] from = ItineraryGenerator.origin(group.trip(), stops);
        DayPlanner.DayContext ctx = new DayPlanner.DayContext(dayIndex, ItineraryGenerator.dayCount(group.trip()),
                day.getDate(), itinerary.getPace(), from[0], from[1]);
        return jobs.submit(userId, tripId, FEATURE, job -> {
            job.progress(10, (planner.aiEnabled() ? "Re-planning" : "Re-ordering") + " day " + dayIndex);
            DayPlanner.PlannedDay plan = planner.plan(ctx, dayStops, true);
            store.saveDay(tripId, dayIndex, plan);
            events.publish(tripId, TripEvents.Type.ITINERARY, userId);
            events.publish(tripId, TripEvents.Type.APPROVAL, userId);
            job.progress(100, "Day " + dayIndex + " re-planned");
            return views.build(tripId, userId, groupPace(group)).orElseThrow();
        });
    }

    /** Organizer only: change the draft directly. */
    public ItineraryView edit(UUID tripId, UUID userId, PlanEdit edit) {
        access.requireOrganizer(tripId, userId);
        String name = users.findById(userId).map(User::getDisplayName).orElse("The organizer");
        store.applyEdit(tripId, edit, "Changed by " + name + "; code recomputed the times.");
        events.publish(tripId, TripEvents.Type.ITINERARY, userId);
        events.publish(tripId, TripEvents.Type.APPROVAL, userId);
        events.publish(tripId, TripEvents.Type.PROPOSALS, userId);
        return views.build(tripId, userId, groupPace(tripId)).orElseThrow();
    }

    Pace groupPace(UUID tripId) {
        return groupPace(loader.load(tripId));
    }

    static Pace groupPace(TripGroupLoader.TripGroup group) {
        return Pace.forGroup(group.fitMembers().stream().filter(m -> m.profile() != null)
                .map(m -> m.profile().pace()).toList());
    }
}
