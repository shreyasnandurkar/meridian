package com.meridian.trip;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

/** Composite key (trip, user) shared by membership and per-trip preferences. */
@Embeddable
public record TripUserId(
        @Column(name = "trip_id") UUID tripId,
        @Column(name = "user_id") UUID userId) implements Serializable {}
