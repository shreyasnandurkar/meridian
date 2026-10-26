package com.meridian.trip;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TripMemberRepository extends JpaRepository<TripMember, TripUserId> {

    List<TripMember> findByIdTripIdOrderByJoinedAt(UUID tripId);

    List<TripMember> findByIdUserId(UUID userId);

    @Query("select m.id.tripId as tripId, count(m) as members from TripMember m "
            + "where m.id.tripId in :tripIds group by m.id.tripId")
    List<MemberCount> countByTrip(List<UUID> tripIds);

    interface MemberCount {
        UUID getTripId();

        long getMembers();
    }
}
