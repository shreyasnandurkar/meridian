package com.meridian.decision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One member's vote on a change proposal (spec §5.8). Every member's vote weighs the same. */
@Entity
@Table(name = "votes")
public class Vote {

    public enum Value { APPROVE, REJECT }

    @Id
    private UUID id;

    @Column(name = "proposal_id", nullable = false)
    private UUID proposalId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Value value;

    @Column(name = "voted_at", nullable = false)
    private Instant votedAt;

    protected Vote() {}

    public Vote(UUID proposalId, UUID userId, Value value) {
        this.id = UUID.randomUUID();
        this.proposalId = proposalId;
        this.userId = userId;
        this.value = value;
        this.votedAt = Instant.now();
    }

    public void change(Value value) {
        this.value = value;
        this.votedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProposalId() {
        return proposalId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Value getValue() {
        return value;
    }

    public Instant getVotedAt() {
        return votedAt;
    }
}
