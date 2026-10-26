package com.meridian.itinerary;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryRepository extends JpaRepository<Itinerary, UUID> {

    Optional<Itinerary> findByTripId(UUID tripId);
}
