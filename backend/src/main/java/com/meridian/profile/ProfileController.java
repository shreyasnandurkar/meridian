package com.meridian.profile;

import com.meridian.ai.job.AiJobView;
import com.meridian.common.CurrentUser;
import com.meridian.profile.ProfileModels.QuizAnswers;
import com.meridian.profile.ProfileModels.TravelProfile;
import com.meridian.profile.ProfileService.ProfileView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    /** 204 when the user has not taken the quiz yet. */
    @GetMapping
    public ResponseEntity<ProfileView> get(@AuthenticationPrincipal Jwt jwt) {
        return profiles.get(CurrentUser.id(jwt)).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/quiz")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AiJobView submitQuiz(@Valid @RequestBody QuizAnswers quiz, @AuthenticationPrincipal Jwt jwt) {
        return AiJobView.from(profiles.startQuiz(CurrentUser.id(jwt), quiz));
    }

    @PutMapping
    public ProfileView update(@Valid @RequestBody TravelProfile profile, @AuthenticationPrincipal Jwt jwt) {
        return profiles.update(CurrentUser.id(jwt), profile);
    }

    /** Single source of truth for quiz/profile options in the UI. */
    @GetMapping("/vocabulary")
    public Map<String, Object> vocabulary() {
        return Map.of(
                "interests", Vocabulary.INTERESTS.stream()
                        .map(i -> Map.of("id", i.tag(), "label", i.label(), "headline", i.headline()))
                        .toList(),
                "paces", Vocabulary.PACES,
                "budgetLevels", Vocabulary.BUDGET_LEVELS,
                "dietary", Vocabulary.DIETARY,
                "accessNeeds", Vocabulary.ACCESS_NEEDS,
                "limits", Map.of("mustHaves", 10, "dealbreakers", 10, "idealTrip", 1000, "listItem", 80),
                "headlineInterests", List.copyOf(Vocabulary.HEADLINE_TAGS));
    }
}
