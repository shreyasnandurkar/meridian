package com.meridian.itinerary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A member's suggested change to the itinerary (spec §5.7). The group votes on it; approval applies the edit. */
@Entity
@Table(name = "proposals")
public class Proposal {

    public enum Status { OPEN, WITHDRAWN, APPROVED, REJECTED, STALE }

    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "itinerary_id", nullable = false)
    private UUID itineraryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanEdit.Type type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private PlanEdit payload;

    @Column(nullable = false)
    private String summary;

    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "base_version", nullable = false)
    private int baseVersion;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Proposal() {}

    public Proposal(UUID tripId, Itinerary itinerary, PlanEdit edit, String summary, String reason, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.tripId = tripId;
        this.itineraryId = itinerary.getId();
        this.type = edit.type();
        this.payload = edit;
        this.summary = summary;
        this.reason = reason == null || reason.isBlank() ? null : reason.strip();
        this.status = Status.OPEN;
        this.baseVersion = itinerary.getVersion();
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public boolean isOpen() {
        return status == Status.OPEN;
    }

    public void resolve(Status status) {
        this.status = status;
        this.resolvedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTripId() {
        return tripId;
    }

    public UUID getItineraryId() {
        return itineraryId;
    }

    public PlanEdit.Type getType() {
        return type;
    }

    public PlanEdit getPayload() {
        return payload;
    }

    public String getSummary() {
        return summary;
    }

    public String getReason() {
        return reason;
    }

    public Status getStatus() {
        return status;
    }

    public int getBaseVersion() {
        return baseVersion;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
