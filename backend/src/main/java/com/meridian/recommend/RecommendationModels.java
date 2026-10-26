package com.meridian.recommend;

import com.meridian.place.PlaceCategory;
import com.meridian.place.PlaceDtos.PlaceView;
import com.meridian.profile.ProfileModels.Provenance;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class RecommendationModels {

    private RecommendationModels() {}

    /**
     * @param gems        hidden-gems ↔ classics slider, 0–100; null means the group's average setting
     * @param avoidCrowds drop chains and push famous places down
     */
    public record RecommendationQuery(@NotNull PlaceCategory category, @Min(0) @Max(100) Integer gems,
            boolean avoidCrowds, boolean regenerate) {}

    /**
     * One suggested place. {@code score} and {@code rulesRank} come from code; {@code rank} is the AI's order when
     * {@code why} came from AI. {@code sponsored} is always false today: no paid placement exists, and the field is
     * only there so a flagged item could never be shown without its label.
     */
    public record Recommendation(
            PlaceView place,
            int rank,
            int rulesRank,
            int score,
            String why,
            List<String> matchedInterests,
            List<String> fitsMembers,
            List<String> missesMembers,
            List<String> localSignals,
            double distanceKm,
            boolean sponsored) {}

    public record RecommendationSet(
            PlaceCategory category,
            int gems,
            int groupGems,
            boolean avoidCrowds,
            List<Recommendation> items,
            int candidates,
            String placesSource,
            Instant placesCheckedAt,
            boolean placesStale,
            Provenance provenance,
            String inputKey,
            boolean aiAvailable) {}
}
