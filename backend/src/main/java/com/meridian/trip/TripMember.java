package com.meridian.trip;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip_members")
public class TripMember {

    public enum Role { ORGANIZER, MEMBER }

    @EmbeddedId
    private TripUserId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected TripMember() {}

    public TripMember(UUID tripId, UUID userId, Role role) {
        this.id = new TripUserId(tripId, userId);
        this.role = role;
        this.joinedAt = Instant.now();
    }

    public UUID getTripId() {
        return id.tripId();
    }

    public UUID getUserId() {
        return id.userId();
    }

    public Role getRole() {
        return role;
    }

    public boolean isOrganizer() {
        return role == Role.ORGANIZER;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
