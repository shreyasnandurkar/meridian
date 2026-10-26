package com.meridian.shortlist;

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

/** A member's love / ok / no-way on a shortlist item; one per member per item. */
@Entity
@Table(name = "reactions")
public class Reaction {

    public enum Value { LOVE, OK, NO_WAY }

    @Embeddable
    public record Key(@Column(name = "item_id") UUID itemId, @Column(name = "user_id") UUID userId)
            implements Serializable {}

    @EmbeddedId
    private Key id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Value value;

    private String reason;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Reaction() {}

    public Reaction(UUID itemId, UUID userId) {
        this.id = new Key(itemId, userId);
    }

    public void set(Value value, String reason) {
        this.value = value;
        this.reason = reason == null || reason.isBlank() ? null : reason.strip();
        this.updatedAt = Instant.now();
    }

    public UUID getItemId() {
        return id.itemId();
    }

    public UUID getUserId() {
        return id.userId();
    }

    public Value getValue() {
        return value;
    }

    public String getReason() {
        return reason;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
