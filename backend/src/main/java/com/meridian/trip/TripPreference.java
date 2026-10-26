package com.meridian.trip;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A member's per-trip overrides. The budget is private unless {@code budgetVisible} is set (spec §5.2). */
@Entity
@Table(name = "trip_preferences")
public class TripPreference {

    @EmbeddedId
    private TripUserId id;

    @Column(name = "budget_min")
    private BigDecimal budgetMin;

    @Column(name = "budget_max")
    private BigDecimal budgetMax;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "must_haves", nullable = false)
    private List<String> mustHaves = List.of();

    @Column(name = "budget_visible", nullable = false)
    private boolean budgetVisible;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TripPreference() {}

    public TripPreference(UUID tripId, UUID userId) {
        this.id = new TripUserId(tripId, userId);
        this.updatedAt = Instant.now();
    }

    public void update(BigDecimal budgetMin, BigDecimal budgetMax, List<String> mustHaves, boolean budgetVisible) {
        this.budgetMin = budgetMin;
        this.budgetMax = budgetMax;
        this.mustHaves = List.copyOf(mustHaves);
        this.budgetVisible = budgetVisible;
        this.updatedAt = Instant.now();
    }

    public BigDecimal getBudgetMin() {
        return budgetMin;
    }

    public BigDecimal getBudgetMax() {
        return budgetMax;
    }

    public List<String> getMustHaves() {
        return mustHaves;
    }

    public boolean isBudgetVisible() {
        return budgetVisible;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
