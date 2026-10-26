package com.meridian.trip;

import com.meridian.common.ApiException;
import com.meridian.config.MeridianProperties;
import com.meridian.trip.TripDtos.AcceptResult;
import com.meridian.trip.TripDtos.InvitePreview;
import com.meridian.trip.TripDtos.InviteView;
import com.meridian.user.UserRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InviteService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final InviteRepository invites;
    private final TripRepository trips;
    private final TripMemberRepository members;
    private final UserRepository users;
    private final TripAccess access;
    private final String appBaseUrl;

    public InviteService(InviteRepository invites, TripRepository trips, TripMemberRepository members,
            UserRepository users, TripAccess access, MeridianProperties props) {
        this.invites = invites;
        this.trips = trips;
        this.members = members;
        this.users = users;
        this.access = access;
        this.appBaseUrl = props.appBaseUrl().replaceAll("/+$", "");
    }

    @Transactional
    public InviteView create(UUID tripId, UUID userId) {
        access.requireOrganizer(tripId, userId);
        return view(invites.save(new Invite(tripId, newToken(), userId)));
    }

    @Transactional(readOnly = true)
    public List<InviteView> listActive(UUID tripId, UUID userId) {
        access.requireOrganizer(tripId, userId);
        return invites.findByTripIdAndRevokedAtIsNullOrderByCreatedAtDesc(tripId).stream().map(this::view).toList();
    }

    @Transactional
    public void revoke(UUID tripId, UUID inviteId, UUID userId) {
        access.requireOrganizer(tripId, userId);
        Invite invite = invites.findById(inviteId)
                .filter(i -> i.getTripId().equals(tripId))
                .orElseThrow(() -> ApiException.notFound("Invite not found"));
        invite.revoke();
    }

    /** Public: what the join page shows before the visitor signs in. */
    @Transactional(readOnly = true)
    public InvitePreview preview(String token) {
        Invite invite = activeInvite(token);
        Trip trip = trips.findById(invite.getTripId()).orElseThrow(() -> invalidLink());
        String organizer = users.findById(trip.getCreatedBy()).map(u -> u.getDisplayName()).orElse("The organizer");
        long count = members.findByIdTripIdOrderByJoinedAt(trip.getId()).size();
        return new InvitePreview(trip.getName(), trip.getDestinationName(), trip.getStartDate(), trip.getEndDate(),
                organizer, count);
    }

    /** Joining is idempotent: following the link again just opens the trip. */
    @Transactional
    public AcceptResult accept(String token, UUID userId) {
        Invite invite = activeInvite(token);
        TripUserId key = new TripUserId(invite.getTripId(), userId);
        if (members.existsById(key)) return new AcceptResult(invite.getTripId(), true);
        members.save(new TripMember(invite.getTripId(), userId, TripMember.Role.MEMBER));
        return new AcceptResult(invite.getTripId(), false);
    }

    private Invite activeInvite(String token) {
        return invites.findByToken(token).filter(Invite::isActive).orElseThrow(InviteService::invalidLink);
    }

    private static ApiException invalidLink() {
        return ApiException.notFound("This invite link is not valid any more. Ask the organizer for a new one.");
    }

    private InviteView view(Invite invite) {
        return new InviteView(invite.getId(), invite.getToken(), appBaseUrl + "/join/" + invite.getToken(),
                invite.getCreatedAt());
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
