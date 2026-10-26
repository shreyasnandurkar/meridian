package com.meridian.trip;

import com.meridian.common.ApiException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trip-membership authorization used by every trip-scoped endpoint. Non-members get 404 so trip ids cannot be probed.
 */
@Component
public class TripAccess {

    private final TripMemberRepository members;

    public TripAccess(TripMemberRepository members) {
        this.members = members;
    }

    @Transactional(readOnly = true)
    public TripMember requireMember(UUID tripId, UUID userId) {
        return members.findById(new TripUserId(tripId, userId))
                .orElseThrow(() -> ApiException.notFound("Trip not found"));
    }

    @Transactional(readOnly = true)
    public TripMember requireOrganizer(UUID tripId, UUID userId) {
        TripMember member = requireMember(tripId, userId);
        if (!member.isOrganizer()) {
            throw ApiException.forbidden("Only the trip organizer can do this");
        }
        return member;
    }
}
