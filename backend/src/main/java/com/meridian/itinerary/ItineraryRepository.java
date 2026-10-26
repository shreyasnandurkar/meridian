package com.meridian.itinerary;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ItineraryRepository extends JpaRepository<Itinerary, UUID> {

    Optional<Itinerary> findByTripId(UUID tripId);

    /** Votes, locking and applying approved changes take this row lock, so one trip's decisions happen in turn. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Itinerary i where i.tripId = :tripId")
    Optional<Itinerary> lockByTripId(UUID tripId);
}
