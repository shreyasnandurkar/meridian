package com.meridian.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** Value types for traveler profiles, stored as JSONB. */
public final class ProfileModels {

    private ProfileModels() {}

    public record Budget(@NotBlank String stay, @NotBlank String food, @NotBlank String activities) {}

    /** Raw onboarding quiz answers. Vocabulary membership is checked by {@link ProfileValidator}. */
    public record QuizAnswers(
            @NotNull Map<String, @NotNull @Min(0) @Max(4) Integer> interestRatings,
            @NotBlank String pace,
            @NotNull @Valid Budget budget,
            @Min(0) @Max(100) int crowdTolerance,
            @Min(0) @Max(100) int hiddenGems,
            @Size(max = 10) List<String> dietary,
            @Size(max = 10) List<String> accessNeeds,
            @Size(max = 8) List<@Size(max = 80) String> mustHaves,
            @Size(max = 8) List<@Size(max = 80) String> dealbreakers,
            @Size(max = 1000) String idealTrip) {

        public QuizAnswers {
            dietary = dietary == null ? List.of() : dietary;
            accessNeeds = accessNeeds == null ? List.of() : accessNeeds;
            mustHaves = mustHaves == null ? List.of() : mustHaves;
            dealbreakers = dealbreakers == null ? List.of() : dealbreakers;
            idealTrip = idealTrip == null ? "" : idealTrip.strip();
        }
    }

    /**
     * The structured travel-style profile (A1). {@code interests} maps vocabulary tags to weights in [0, 1];
     * {@code hiddenGems} runs from 0 (classics) to 100 (hidden gems).
     */
    public record TravelProfile(
            @NotNull Map<String, Double> interests,
            @NotBlank String pace,
            @NotNull @Valid Budget budget,
            @Min(0) @Max(100) int crowdTolerance,
            @Min(0) @Max(100) int hiddenGems,
            @NotNull List<String> dietary,
            @NotNull List<String> accessNeeds,
            @NotNull @Size(max = 10) List<@Size(max = 80) String> mustHaves,
            @NotNull @Size(max = 10) List<@Size(max = 80) String> dealbreakers,
            @Size(max = 300) String summary) {}

    /** One thing the profile builder inferred from the free-text answer, with the words it was based on. */
    public record Inference(String field, String value, String evidence) {}

    /** Why the profile looks the way it does — rendered in the "Why?" popover. */
    public record Provenance(
            String source, // "ai" or "rules"
            String model,
            String promptVersion,
            List<Inference> inferences,
            String note) {}

    /** What the model is asked to produce: only the parts that need reading free text. */
    public record AiExtraction(
            List<ExtraInterest> extraInterests,
            List<String> mustHaves,
            List<String> dealbreakers,
            String summary) {

        public record ExtraInterest(String tag, double weight, String evidence) {}
    }
}
