package com.meridian.decision;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * A member's verdict on the itinerary as a whole, for the consensus gate. It counts only while {@code version} is
 * the itinerary's current version.
 */
@Entity
@Table(name = "plan_approvals")
public class PlanApproval {

    @Embeddable
    public record Key(
            @Column(name = "itinerary_id") UUID itineraryId,
            @Column(name = "user_id") UUID userId) implements Serializable {}

    @EmbeddedId
    private Key id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Vote.Value value;

    @Column(nullable = false)
    private int version;

    @Column(name = "voted_at", nullable = false)
    private Instant votedAt;

    protected PlanApproval() {}

    public PlanApproval(UUID itineraryId, UUID userId) {
        this.id = new Key(itineraryId, userId);
    }

    public void set(Vote.Value value, int version) {
        this.value = value;
        this.version = version;
        this.votedAt = Instant.now();
    }

    /** The plan changed in a way this member approved, so their approval still stands. */
    public void carryTo(int version) {
        this.version = version;
    }

    public UUID getUserId() {
        return id.userId();
    }

    public Vote.Value getValue() {
        return value;
    }

    public int getVersion() {
        return version;
    }

    public Instant getVotedAt() {
        return votedAt;
    }
}
