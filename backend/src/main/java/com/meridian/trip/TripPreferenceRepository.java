package com.meridian.trip;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripPreferenceRepository extends JpaRepository<TripPreference, TripUserId> {

    List<TripPreference> findByIdTripId(UUID tripId);
}
