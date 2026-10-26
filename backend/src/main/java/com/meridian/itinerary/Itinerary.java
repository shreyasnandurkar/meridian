package com.meridian.itinerary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A trip's itinerary. Regenerating replaces its days in place; every change bumps {@code version}. */
@Entity
@Table(name = "itineraries")
public class Itinerary {

    public enum Status { DRAFT, LOCKED }

    /** A shortlisted item that is not on any day, and why. */
    public record Unplaced(UUID shortlistItemId, String reason) {}

    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Pace pace;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Unplaced> unplaced = List.of();

    @Column(name = "generated_by", nullable = false)
    private UUID generatedBy;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by")
    private UUID lockedBy;

    protected Itinerary() {}

    public Itinerary(UUID tripId, Pace pace, UUID generatedBy) {
        this.id = UUID.randomUUID();
        this.tripId = tripId;
        this.status = Status.DRAFT;
        this.version = 1;
        this.pace = pace;
        this.generatedBy = generatedBy;
        this.generatedAt = Instant.now();
        this.updatedAt = generatedAt;
    }

    /** A fresh draft from the generator replaces the old one. */
    public void regenerated(Pace pace, UUID by, List<Unplaced> unplaced) {
        this.pace = pace;
        this.generatedBy = by;
        this.generatedAt = Instant.now();
        this.unplaced = List.copyOf(unplaced);
        changed();
    }

    public void setUnplaced(List<Unplaced> unplaced) {
        this.unplaced = List.copyOf(unplaced);
    }

    /** Any change to days, stops or the unplaced list. */
    public void changed() {
        this.version++;
        this.updatedAt = Instant.now();
    }

    /** Locking freezes the plan; it does not change it, so the version stays. */
    public void lock(UUID by) {
        this.status = Status.LOCKED;
        this.lockedAt = Instant.now();
        this.lockedBy = by;
    }

    public void unlock() {
        this.status = Status.DRAFT;
        this.lockedAt = null;
        this.lockedBy = null;
    }

    public boolean isLocked() {
        return status == Status.LOCKED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTripId() {
        return tripId;
    }

    public Status getStatus() {
        return status;
    }

    public int getVersion() {
        return version;
    }

    public Pace getPace() {
        return pace;
    }

    public List<Unplaced> getUnplaced() {
        return unplaced;
    }

    public UUID getGeneratedBy() {
        return generatedBy;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public UUID getLockedBy() {
        return lockedBy;
    }
}
