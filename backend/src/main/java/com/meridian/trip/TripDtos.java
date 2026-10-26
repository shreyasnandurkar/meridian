package com.meridian.trip;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TripDtos {

    private TripDtos() {}

    public record CreateTripRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 300) String destinationName,
            @DecimalMin("-90") @DecimalMax("90") Double destinationLat,
            @DecimalMin("-180") @DecimalMax("180") Double destinationLng,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter currency code") String currency,
            @PositiveOrZero BigDecimal budgetTotal) {}

    public record TripSummary(
            UUID id,
            String name,
            String destinationName,
            LocalDate startDate,
            LocalDate endDate,
            TripMember.Role myRole,
            long memberCount) {}

    public record MemberView(
            UUID userId, String displayName, TripMember.Role role, Instant joinedAt, boolean hasProfile) {}

    public record QuorumView(int requiredApprovals, Integer minApprovals, String description) {}

    public record TripDetail(
            UUID id,
            String name,
            String destinationName,
            Double destinationLat,
            Double destinationLng,
            LocalDate startDate,
            LocalDate endDate,
            int days,
            String currency,
            BigDecimal budgetTotal,
            QuorumView quorum,
            TripMember.Role myRole,
            List<MemberView> members,
            Instant createdAt) {}

    public record QuorumRequest(@Min(1) @Max(50) Integer minApprovals) {}

    public record InviteView(UUID id, String token, String url, Instant createdAt) {}

    public record InvitePreview(
            String tripName,
            String destinationName,
            LocalDate startDate,
            LocalDate endDate,
            String organizerName,
            long memberCount) {}

    public record AcceptResult(UUID tripId, boolean alreadyMember) {}

    public record PreferenceRequest(
            @PositiveOrZero BigDecimal budgetMin,
            @PositiveOrZero BigDecimal budgetMax,
            @Size(max = 10) List<@NotBlank @Size(max = 80) String> mustHaves,
            boolean budgetVisible) {}

    public record PreferenceView(
            BigDecimal budgetMin, BigDecimal budgetMax, List<String> mustHaves, boolean budgetVisible, String currency,
            Instant updatedAt) {}
}
