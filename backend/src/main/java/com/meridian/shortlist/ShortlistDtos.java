package com.meridian.shortlist;

import com.meridian.place.PlaceCategory;
import com.meridian.place.PlaceDtos.PlaceView;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ShortlistDtos {

    private ShortlistDtos() {}

    /**
     * Add by one of three routes: {@code PLACE} needs {@code placeId}; {@code LINK} needs {@code url} (the title is read
     * from the page when left out); {@code PIN} needs {@code title}, {@code lat} and {@code lng}.
     */
    public record AddItemRequest(
            @NotNull ShortlistItem.Kind kind,
            UUID placeId,
            @Size(max = 2048) String url,
            @Size(max = 300) String title,
            @DecimalMin("-90") @DecimalMax("90") Double lat,
            @DecimalMin("-180") @DecimalMax("180") Double lng,
            PlaceCategory category,
            @Size(max = 500) String reason,
            @Size(max = 2000) String notes) {}

    /** Partial update: null leaves a field as it is, an empty string clears reason or notes. */
    public record UpdateItemRequest(
            PlaceCategory category,
            @Size(max = 300) String title,
            @Size(max = 500) String reason,
            @Size(max = 2000) String notes) {}

    public record ReactionRequest(@NotNull Reaction.Value value, @Size(max = 300) String reason) {}

    public record Person(UUID userId, String displayName) {}

    public record ReactionView(UUID userId, String displayName, Reaction.Value value, String reason,
            Instant updatedAt) {}

    public record ItemView(
            UUID id,
            ShortlistItem.Kind kind,
            String title,
            PlaceCategory category,
            /* set for PLACE items: source, checked-at time, tags */
            PlaceView place,
            String url,
            String host,
            Double lat,
            Double lng,
            Person addedBy,
            String reason,
            String notes,
            Instant createdAt,
            Instant updatedAt,
            List<ReactionView> reactions,
            boolean canEdit) {}

    public record LinkPreviewView(String url, String host, String title) {}
}
