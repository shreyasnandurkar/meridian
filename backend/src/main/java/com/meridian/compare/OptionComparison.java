package com.meridian.compare;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A named side-by-side comparison, e.g. "Stay in North Goa". */
@Entity
@Table(name = "option_comparisons")
public class OptionComparison {

    public enum Kind { STAY, FLIGHT, ACTIVITY, TRANSPORT, OTHER }

    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(nullable = false)
    private String title;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OptionComparison() {}

    public OptionComparison(UUID tripId, Kind kind, String title, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.tripId = tripId;
        this.kind = kind;
        this.title = title;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void edit(Kind kind, String title) {
        if (kind != null) this.kind = kind;
        if (title != null && !title.isBlank()) this.title = title.strip();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTripId() {
        return tripId;
    }

    public Kind getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
