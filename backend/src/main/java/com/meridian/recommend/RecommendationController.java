package com.meridian.recommend;

import com.meridian.ai.job.AiJobView;
import com.meridian.common.CurrentUser;
import com.meridian.place.PlaceCategory;
import com.meridian.recommend.RecommendationModels.RecommendationQuery;
import com.meridian.recommend.RecommendationModels.RecommendationSet;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/trips/{tripId}/recommendations")
public class RecommendationController {

    private final RecommendationService recommendations;

    public RecommendationController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    /** Cached AI suggestions for these settings if they exist, otherwise the rule-based ranking. Never waits on AI. */
    @GetMapping
    public RecommendationSet get(@PathVariable UUID tripId, @RequestParam PlaceCategory category,
            @RequestParam(required = false) @Min(0) @Max(100) Integer gems,
            @RequestParam(defaultValue = "false") boolean avoidCrowds, @AuthenticationPrincipal Jwt jwt) {
        return recommendations.get(tripId, CurrentUser.id(jwt),
                new RecommendationQuery(category, gems, avoidCrowds, false));
    }

    /** A2 re-rank and explanations as a background job. */
    @PostMapping("/ai")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AiJobView startAi(@PathVariable UUID tripId, @Valid @RequestBody RecommendationQuery query,
            @AuthenticationPrincipal Jwt jwt) {
        return AiJobView.from(recommendations.startAi(tripId, CurrentUser.id(jwt), query));
    }
}
