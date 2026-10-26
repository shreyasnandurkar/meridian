package com.meridian.trip;

import com.meridian.common.CurrentUser;
import com.meridian.trip.TripDtos.CreateTripRequest;
import com.meridian.trip.TripDtos.InviteView;
import com.meridian.trip.TripDtos.MemberView;
import com.meridian.trip.TripDtos.PreferenceRequest;
import com.meridian.trip.TripDtos.PreferenceView;
import com.meridian.trip.TripDtos.TripDetail;
import com.meridian.trip.TripDtos.TripSummary;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService trips;
    private final InviteService invites;

    public TripController(TripService trips, InviteService invites) {
        this.trips = trips;
        this.invites = invites;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripDetail create(@Valid @RequestBody CreateTripRequest req, @AuthenticationPrincipal Jwt jwt) {
        return trips.create(CurrentUser.id(jwt), req);
    }

    @GetMapping
    public List<TripSummary> mine(@AuthenticationPrincipal Jwt jwt) {
        return trips.listMine(CurrentUser.id(jwt));
    }

    @GetMapping("/{tripId}")
    public TripDetail get(@PathVariable UUID tripId, @AuthenticationPrincipal Jwt jwt) {
        return trips.get(tripId, CurrentUser.id(jwt));
    }

    @GetMapping("/{tripId}/members")
    public List<MemberView> members(@PathVariable UUID tripId, @AuthenticationPrincipal Jwt jwt) {
        return trips.members(tripId, CurrentUser.id(jwt));
    }

    @GetMapping("/{tripId}/preferences/me")
    public PreferenceView myPreferences(@PathVariable UUID tripId, @AuthenticationPrincipal Jwt jwt) {
        return trips.myPreferences(tripId, CurrentUser.id(jwt));
    }

    @PutMapping("/{tripId}/preferences/me")
    public PreferenceView updateMyPreferences(@PathVariable UUID tripId, @Valid @RequestBody PreferenceRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return trips.updateMyPreferences(tripId, CurrentUser.id(jwt), req);
    }

    @PostMapping("/{tripId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteView createInvite(@PathVariable UUID tripId, @AuthenticationPrincipal Jwt jwt) {
        return invites.create(tripId, CurrentUser.id(jwt));
    }

    @GetMapping("/{tripId}/invites")
    public List<InviteView> activeInvites(@PathVariable UUID tripId, @AuthenticationPrincipal Jwt jwt) {
        return invites.listActive(tripId, CurrentUser.id(jwt));
    }

    @DeleteMapping("/{tripId}/invites/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeInvite(@PathVariable UUID tripId, @PathVariable UUID inviteId, @AuthenticationPrincipal Jwt jwt) {
        invites.revoke(tripId, inviteId, CurrentUser.id(jwt));
    }
}
