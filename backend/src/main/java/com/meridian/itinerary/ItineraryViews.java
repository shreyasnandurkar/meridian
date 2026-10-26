package com.meridian.itinerary;

import com.meridian.itinerary.ItineraryDtos.DayView;
import com.meridian.itinerary.ItineraryDtos.ItineraryView;
import com.meridian.itinerary.ItineraryDtos.StopView;
import com.meridian.itinerary.ItineraryDtos.UnplacedView;
import com.meridian.profile.ProfileModels.Provenance;
import com.meridian.shortlist.ShortlistDtos.Person;
import com.meridian.shortlist.ShortlistItem;
import com.meridian.trip.TripMember;
import com.meridian.trip.TripMemberRepository;
import com.meridian.trip.TripUserId;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Builds what the Itinerary tab shows, for one viewer. */
@Component
public class ItineraryViews {

    static final String AI_NOTE = "Code grouped the shortlist into days by distance and checked every travel time; "
            + "the AI chose the order and start times for this day and wrote the notes.";
    static final String RULES_NOTE = "Code grouped the shortlist into days by distance, visited the nearest place next "
            + "each time and put food stops nearest lunch and dinner.";

    private final ItineraryRepository itineraries;
    private final ItineraryDayRepository days;
    private final ItineraryItemRepository items;
    private final ProposalRepository proposals;
    private final CommentRepository comments;
    private final StopFactory stopFactory;
    private final TripMemberRepository members;
    private final UserRepository users;
    private final DayPlanner planner;

    public ItineraryViews(ItineraryRepository itineraries, ItineraryDayRepository days, ItineraryItemRepository items,
            ProposalRepository proposals, CommentRepository comments, StopFactory stopFactory,
            TripMemberRepository members, UserRepository users, DayPlanner planner) {
        this.itineraries = itineraries;
        this.days = days;
        this.items = items;
        this.proposals = proposals;
        this.comments = comments;
        this.stopFactory = stopFactory;
        this.members = members;
        this.users = users;
        this.planner = planner;
    }

    @Transactional(readOnly = true)
    public Optional<ItineraryView> build(UUID tripId, UUID viewerId, Pace groupPace) {
        Itinerary itinerary = itineraries.findByTripId(tripId).orElse(null);
        if (itinerary == null) return Optional.empty();
        List<ItineraryDay> dayList = days.findByItineraryIdOrderByDayIndex(itinerary.getId());
        List<ItineraryItem> all = items.findByItineraryIdOrderByPosition(itinerary.getId());
        StopFactory.TripStops stops = stopFactory.load(tripId, false);
        Map<UUID, Long> commentCounts = comments.findByTripIdOrderByCreatedAt(tripId).stream()
                .filter(c -> c.getTargetType() == Comment.Target.SHORTLIST_ITEM)
                .collect(Collectors.groupingBy(Comment::getTargetId, Collectors.counting()));
        Set<UUID> people = new HashSet<>();
        people.add(itinerary.getGeneratedBy());
        stops.items().values().forEach(i -> people.add(i.getAddedBy()));
        Map<UUID, String> names = users.findAllById(people).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));
        Function<UUID, Person> person = id -> new Person(id, names.getOrDefault(id, "Former member"));
        Pace pace = itinerary.getPace();

        List<DayView> dayViews = new ArrayList<>();
        Set<UUID> onPlan = new HashSet<>();
        for (ItineraryDay day : dayList) {
            List<StopView> stopViews = new ArrayList<>();
            LocalTime lastEnd = null;
            for (ItineraryItem it : all) {
                if (!it.getDayId().equals(day.getId())) continue;
                ShortlistItem si = stops.items().get(it.getShortlistItemId());
                if (si == null) continue;
                onPlan.add(si.getId());
                Stop stop = stops.stops().get(si.getId());
                LocalTime end = it.getStartTime().plusMinutes(it.getDurationMin());
                lastEnd = end;
                stopViews.add(new StopView(it.getId(), si.getId(), stops.titles().get(si.getId()), si.getCategory(),
                        stop == null ? null : stop.lat(), stop == null ? null : stop.lng(),
                        it.getStartTime().toString(), end.toString(), it.getDurationMin(), it.getTravelMin(),
                        it.getTravelKm(), it.getNote(), person.apply(si.getAddedBy()), si.getReason(),
                        commentCounts.getOrDefault(si.getId(), 0L).intValue()));
            }
            int overflow = lastEnd == null ? 0
                    : Math.max(0, Scheduler.minutes(lastEnd) - Scheduler.minutes(pace.dayEnd()));
            boolean ai = day.getSource() == ItineraryDay.Source.AI;
            Provenance provenance = new Provenance(ai ? "ai" : "rules", day.getModel(), day.getPromptVersion(),
                    List.of(), ai ? AI_NOTE : RULES_NOTE);
            dayViews.add(new DayView(day.getDayIndex(), day.getDate(), day.getSummary(), provenance, day.getRepairs(),
                    pace.dayStart().toString(), pace.dayEnd().toString(), overflow, stopViews));
        }

        Map<UUID, String> reasons = new HashMap<>();
        itinerary.getUnplaced().forEach(u -> reasons.put(u.shortlistItemId(), u.reason()));
        stops.unplaceable().forEach(u -> reasons.put(u.shortlistItemId(), u.reason()));
        List<UnplacedView> unplaced = stops.items().values().stream()
                .filter(i -> !onPlan.contains(i.getId()))
                .map(i -> new UnplacedView(i.getId(), stops.titles().get(i.getId()), i.getCategory(),
                        reasons.getOrDefault(i.getId(), "Added to the shortlist after this draft was made."),
                        stops.stops().containsKey(i.getId())))
                .toList();

        int open = proposals.findByItineraryIdAndStatus(itinerary.getId(), Proposal.Status.OPEN).size();
        boolean organizer = members.findById(new TripUserId(tripId, viewerId)).map(TripMember::isOrganizer)
                .orElse(false);
        return Optional.of(new ItineraryView(itinerary.getId(), itinerary.getStatus(), itinerary.getVersion(), pace,
                groupPace, itinerary.getGeneratedAt(), person.apply(itinerary.getGeneratedBy()),
                itinerary.getUpdatedAt(), dayViews, unplaced, open, organizer && !itinerary.isLocked(),
                planner.aiEnabled()));
    }
}
