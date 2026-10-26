package com.meridian.itinerary;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * One change to the itinerary, made directly by the organizer or proposed by a member. Stops are named by their
 * shortlist item id and days by number, so a proposal still means the same thing after other edits.
 *
 * <ul>
 *   <li>{@code MOVE}: {@code itemId} to {@code day} at {@code position} (0-based; null = end of the day)</li>
 *   <li>{@code SWAP}: {@code itemId} and {@code otherItemId} trade places</li>
 *   <li>{@code REMOVE}: {@code itemId} comes off the plan</li>
 *   <li>{@code ADD}: shortlisted {@code itemId}, not yet on the plan, to {@code day} at {@code position}</li>
 * </ul>
 */
public record PlanEdit(
        @NotNull Type type,
        @NotNull UUID itemId,
        UUID otherItemId,
        @Min(1) @Max(60) Integer day,
        @Min(0) @Max(50) Integer position) {

    public enum Type { MOVE, SWAP, REMOVE, ADD }
}
