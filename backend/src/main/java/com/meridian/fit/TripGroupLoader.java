package com.meridian.fit;

import com.meridian.common.ApiException;
import com.meridian.fit.FitScorer.FitMember;
import com.meridian.fit.GroupFitCalculator.GroupMember;
import com.meridian.profile.TravelerProfileEntity;
import com.meridian.profile.TravelerProfileRepository;
import com.meridian.trip.Trip;
import com.meridian.trip.TripMember;
import com.meridian.trip.TripMemberRepository;
import com.meridian.trip.TripPreference;
import com.meridian.trip.TripPreferenceRepository;
import com.meridian.trip.TripRepository;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Loads a trip's members with their profiles and private per-trip budgets, in join order. */
@Component
public class TripGroupLoader {

    public record TripGroup(Trip trip, List<GroupMember> members, List<TripMember> memberships) {

        public List<FitMember> fitMembers() {
            return members.stream().map(GroupMember::fit).toList();
        }
    }

    private final TripRepository trips;
    private final TripMemberRepository memberships;
    private final TripPreferenceRepository preferences;
    private final TravelerProfileRepository profiles;
    private final UserRepository users;

    public TripGroupLoader(TripRepository trips, TripMemberRepository memberships,
            TripPreferenceRepository preferences, TravelerProfileRepository profiles, UserRepository users) {
        this.trips = trips;
        this.memberships = memberships;
        this.preferences = preferences;
        this.profiles = profiles;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public TripGroup load(UUID tripId) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> ApiException.notFound("Trip not found"));
        List<TripMember> list = memberships.findByIdTripIdOrderByJoinedAt(tripId);
        List<UUID> ids = list.stream().map(TripMember::getUserId).toList();
        Map<UUID, String> names = users.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));
        Map<UUID, TravelerProfileEntity> byUser = profiles.findAllById(ids).stream()
                .collect(Collectors.toMap(TravelerProfileEntity::getUserId, Function.identity()));
        Map<UUID, TripPreference> prefs = preferences.findByIdTripId(tripId).stream()
                .collect(Collectors.toMap(TripPreference::getUserId, Function.identity()));
        List<GroupMember> members = list.stream().map(m -> {
            TravelerProfileEntity profile = byUser.get(m.getUserId());
            TripPreference pref = prefs.get(m.getUserId());
            FitMember fit = new FitMember(m.getUserId(), names.getOrDefault(m.getUserId(), "Member"),
                    profile == null ? null : profile.getProfile(), pref == null ? List.of() : pref.getMustHaves());
            return pref == null
                    ? new GroupMember(fit, null, null, false)
                    : new GroupMember(fit, pref.getBudgetMin(), pref.getBudgetMax(), pref.isBudgetVisible());
        }).toList();
        return new TripGroup(trip, members, list);
    }
}
