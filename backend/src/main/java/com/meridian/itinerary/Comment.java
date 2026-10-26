package com.meridian.itinerary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A flat comment (spec §5.7). Comments on a place hang off its shortlist item, so they survive the itinerary being
 * regenerated and show on both the shortlist and the day plan.
 */
@Entity
@Table(name = "comments")
public class Comment {

    public enum Target { SHORTLIST_ITEM, PROPOSAL }

    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private Target targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Comment() {}

    public Comment(UUID tripId, Target targetType, UUID targetId, UUID authorId, String body) {
        this.id = UUID.randomUUID();
        this.tripId = tripId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.authorId = authorId;
        this.body = body.strip();
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTripId() {
        return tripId;
    }

    public Target getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
