package com.meridian.trip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "trips")
public class Trip {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "destination_name", nullable = false)
    private String destinationName;

    @Column(name = "destination_lat")
    private Double destinationLat;

    @Column(name = "destination_lng")
    private Double destinationLng;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private String currency;

    @Column(name = "budget_total")
    private BigDecimal budgetTotal;

    @Column(name = "quorum_min_approvals")
    private Integer quorumMinApprovals;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Trip() {}

    public Trip(String name, String destinationName, Double destinationLat, Double destinationLng, LocalDate startDate,
            LocalDate endDate, String currency, BigDecimal budgetTotal, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.destinationName = destinationName;
        this.destinationLat = destinationLat;
        this.destinationLng = destinationLng;
        this.startDate = startDate;
        this.endDate = endDate;
        this.currency = currency;
        this.budgetTotal = budgetTotal;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public Double getDestinationLat() {
        return destinationLat;
    }

    public Double getDestinationLng() {
        return destinationLng;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBudgetTotal() {
        return budgetTotal;
    }

    public Integer getQuorumMinApprovals() {
        return quorumMinApprovals;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
