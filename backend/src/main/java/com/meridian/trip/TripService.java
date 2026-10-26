package com.meridian.trip;

import com.meridian.common.ApiException;
import com.meridian.profile.TravelerProfileEntity;
import com.meridian.profile.TravelerProfileRepository;
import com.meridian.trip.TripDtos.CreateTripRequest;
import com.meridian.trip.TripDtos.MemberView;
import com.meridian.trip.TripDtos.PreferenceRequest;
import com.meridian.trip.TripDtos.PreferenceView;
import com.meridian.trip.TripDtos.QuorumView;
import com.meridian.trip.TripDtos.TripDetail;
import com.meridian.trip.TripDtos.TripSummary;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {

    static final int MAX_TRIP_DAYS = 30;

    private final TripRepository trips;
    private final TripMemberRepository members;
    private final TripPreferenceRepository preferences;
    private final UserRepository users;
    private final TravelerProfileRepository profiles;
    private final TripAccess access;

    public TripService(TripRepository trips, TripMemberRepository members, TripPreferenceRepository preferences,
            UserRepository users, TravelerProfileRepository profiles, TripAccess access) {
        this.trips = trips;
        this.members = members;
        this.preferences = preferences;
        this.users = users;
        this.profiles = profiles;
        this.access = access;
    }

    @Transactional
    public TripDetail create(UUID userId, CreateTripRequest req) {
        if (req.endDate().isBefore(req.startDate())) {
            throw ApiException.badRequest("The trip must end on or after its start date");
        }
        if (ChronoUnit.DAYS.between(req.startDate(), req.endDate()) + 1 > MAX_TRIP_DAYS) {
            throw ApiException.badRequest("Trips can be at most " + MAX_TRIP_DAYS + " days long");
        }
        if ((req.destinationLat() == null) != (req.destinationLng() == null)) {
            throw ApiException.badRequest("Destination latitude and longitude must be given together");
        }
        Trip trip = trips.save(new Trip(req.name().strip(), req.destinationName().strip(), req.destinationLat(),
                req.destinationLng(), req.startDate(), req.endDate(), req.currency(), req.budgetTotal(), userId));
        members.save(new TripMember(trip.getId(), userId, TripMember.Role.ORGANIZER));
        return detail(trip, TripMember.Role.ORGANIZER);
    }

    @Transactional(readOnly = true)
    public List<TripSummary> listMine(UUID userId) {
        Map<UUID, TripMember.Role> roles = members.findByIdUserId(userId).stream()
                .collect(Collectors.toMap(TripMember::getTripId, TripMember::getRole));
        if (roles.isEmpty()) return List.of();
        List<UUID> ids = List.copyOf(roles.keySet());
        Map<UUID, Long> counts = members.countByTrip(ids).stream()
                .collect(Collectors.toMap(TripMemberRepository.MemberCount::getTripId,
                        TripMemberRepository.MemberCount::getMembers));
        return trips.findAllById(ids).stream()
                .sorted(Comparator.comparing(Trip::getStartDate).thenComparing(Trip::getName))
                .map(t -> new TripSummary(t.getId(), t.getName(), t.getDestinationName(), t.getStartDate(),
                        t.getEndDate(), roles.get(t.getId()), counts.getOrDefault(t.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public TripDetail get(UUID tripId, UUID userId) {
        TripMember me = access.requireMember(tripId, userId);
        return detail(trips.findById(tripId).orElseThrow(() -> ApiException.notFound("Trip not found")), me.getRole());
    }

    @Transactional(readOnly = true)
    public List<MemberView> members(UUID tripId, UUID userId) {
        access.requireMember(tripId, userId);
        return memberViews(tripId);
    }

    @Transactional(readOnly = true)
    public PreferenceView myPreferences(UUID tripId, UUID userId) {
        access.requireMember(tripId, userId);
        Trip trip = trips.findById(tripId).orElseThrow();
        return preferences.findById(new TripUserId(tripId, userId))
                .map(p -> view(p, trip))
                .orElse(new PreferenceView(null, null, List.of(), false, trip.getCurrency(), null));
    }

    @Transactional
    public PreferenceView updateMyPreferences(UUID tripId, UUID userId, PreferenceRequest req) {
        access.requireMember(tripId, userId);
        if (req.budgetMin() != null && req.budgetMax() != null && req.budgetMin().compareTo(req.budgetMax()) > 0) {
            throw ApiException.badRequest("Minimum budget cannot be more than the maximum");
        }
        Trip trip = trips.findById(tripId).orElseThrow();
        TripPreference pref = preferences.findById(new TripUserId(tripId, userId))
                .orElseGet(() -> new TripPreference(tripId, userId));
        List<String> mustHaves = req.mustHaves() == null ? List.of()
                : req.mustHaves().stream().map(String::strip).distinct().toList();
        pref.update(req.budgetMin(), req.budgetMax(), mustHaves, req.budgetVisible());
        return view(preferences.save(pref), trip);
    }

    private static PreferenceView view(TripPreference p, Trip trip) {
        return new PreferenceView(p.getBudgetMin(), p.getBudgetMax(), p.getMustHaves(), p.isBudgetVisible(),
                trip.getCurrency(), p.getUpdatedAt());
    }

    private TripDetail detail(Trip trip, TripMember.Role myRole) {
        List<MemberView> memberViews = memberViews(trip.getId());
        int count = memberViews.size();
        QuorumView quorum = new QuorumView(Quorum.requiredApprovals(count, trip.getQuorumMinApprovals()),
                trip.getQuorumMinApprovals(), Quorum.describe(count, trip.getQuorumMinApprovals()));
        int days = (int) ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
        return new TripDetail(trip.getId(), trip.getName(), trip.getDestinationName(), trip.getDestinationLat(),
                trip.getDestinationLng(), trip.getStartDate(), trip.getEndDate(), days, trip.getCurrency(),
                trip.getBudgetTotal(), quorum, myRole, memberViews, trip.getCreatedAt());
    }

    private List<MemberView> memberViews(UUID tripId) {
        List<TripMember> list = members.findByIdTripIdOrderByJoinedAt(tripId);
        List<UUID> userIds = list.stream().map(TripMember::getUserId).toList();
        Map<UUID, User> byId = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Set<UUID> withProfile = profiles.findAllById(userIds).stream()
                .map(TravelerProfileEntity::getUserId)
                .collect(Collectors.toSet());
        return list.stream()
                .map(m -> new MemberView(m.getUserId(), byId.get(m.getUserId()).getDisplayName(), m.getRole(),
                        m.getJoinedAt(), withProfile.contains(m.getUserId())))
                .toList();
    }
}
