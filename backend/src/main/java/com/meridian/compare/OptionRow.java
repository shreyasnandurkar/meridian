package com.meridian.compare;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One provider's offer. A price always carries its currency and the time someone checked it (spec §1 design rules);
 * {@code sponsored} is a label members set, never an ordering input.
 */
@Entity
@Table(name = "option_rows")
public class OptionRow {

    @Id
    private UUID id;

    @Column(name = "comparison_id", nullable = false)
    private UUID comparisonId;

    @Column(nullable = false)
    private String provider;

    private String title;

    private BigDecimal price;

    private String currency;

    @Column(name = "checked_at")
    private Instant checkedAt;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(nullable = false)
    private boolean sponsored;

    private String notes;

    @Column(nullable = false)
    private String adapter;

    @Column(name = "added_by", nullable = false)
    private UUID addedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OptionRow() {}

    public OptionRow(UUID comparisonId, String adapter, UUID addedBy) {
        this.id = UUID.randomUUID();
        this.comparisonId = comparisonId;
        this.adapter = adapter;
        this.addedBy = addedBy;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void setDetails(String provider, String title, String sourceUrl, boolean sponsored, String notes) {
        this.provider = provider;
        this.title = title;
        this.sourceUrl = sourceUrl;
        this.sponsored = sponsored;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    /** Sets or clears the price; a price is stamped with when it was checked. */
    public void setPrice(BigDecimal price, String currency, Instant checkedAt) {
        this.price = price;
        this.currency = price == null ? null : currency;
        this.checkedAt = price == null ? null : checkedAt;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getComparisonId() {
        return comparisonId;
    }

    public String getProvider() {
        return provider;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public boolean isSponsored() {
        return sponsored;
    }

    public String getNotes() {
        return notes;
    }

    public String getAdapter() {
        return adapter;
    }

    public UUID getAddedBy() {
        return addedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
