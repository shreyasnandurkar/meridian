package com.meridian.trip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invites")
public class Invite {

    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected Invite() {}

    public Invite(UUID tripId, String token, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.tripId = tripId;
        this.token = token;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public void revoke() {
        if (revokedAt == null) revokedAt = Instant.now();
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTripId() {
        return tripId;
    }

    public String getToken() {
        return token;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
