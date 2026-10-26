package com.meridian.compare;

import com.meridian.shortlist.ShortlistDtos.Person;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CompareDtos {

    private CompareDtos() {}

    public record ComparisonRequest(@NotNull OptionComparison.Kind kind, @NotBlank @Size(max = 120) String title) {}

    public record ComparisonUpdate(OptionComparison.Kind kind, @Size(max = 120) String title) {}

    /**
     * Add or replace a row. {@code checkedAt} defaults to now when a price is given; {@code adapter} records which
     * provider adapter filled the draft ("manual" when typed).
     */
    public record RowRequest(
            @NotBlank @Size(max = 80) String provider,
            @Size(max = 300) String title,
            @PositiveOrZero BigDecimal price,
            @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter currency code") String currency,
            Instant checkedAt,
            @Size(max = 2048) String sourceUrl,
            boolean sponsored,
            @Size(max = 1000) String notes,
            @Size(max = 30) String adapter) {}

    public record RowView(
            UUID id,
            String provider,
            String title,
            BigDecimal price,
            String currency,
            Instant checkedAt,
            boolean stale,
            String sourceUrl,
            String sourceHost,
            boolean sponsored,
            String notes,
            String adapter,
            Person addedBy,
            boolean cheapest,
            boolean canEdit) {}

    public record ComparisonView(
            UUID id,
            OptionComparison.Kind kind,
            String title,
            Person createdBy,
            Instant createdAt,
            List<RowView> rows,
            boolean canEdit) {}

    public record DraftView(String provider, String title, BigDecimal price, String currency, String sourceUrl,
            String adapter) {}
}
