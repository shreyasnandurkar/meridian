package com.meridian.trip;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TripPreferenceRepository extends JpaRepository<TripPreference, TripUserId> {}
