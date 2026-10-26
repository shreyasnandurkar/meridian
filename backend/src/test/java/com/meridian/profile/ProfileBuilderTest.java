package com.meridian.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.meridian.profile.ProfileModels.AiExtraction;
import com.meridian.profile.ProfileModels.AiExtraction.ExtraInterest;
import com.meridian.profile.ProfileModels.Budget;
import com.meridian.profile.ProfileModels.QuizAnswers;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProfileBuilderTest {

    private static QuizAnswers quiz(String idealTrip) {
        return new QuizAnswers(Map.of("beaches", 4, "nightlife", 0, "history", 2), "balanced",
                new Budget("mid", "low", "high"), 30, 75, List.of("vegetarian", "vegetarian"), List.of(),
                List.of("Sea view"), List.of("Long bus rides"), idealTrip);
    }

    @Test
    void structuredAnswersMapDirectlyOntoProfile() {
        var built = ProfileBuilder.build(quiz(""), ProfileBuilder.ruleExtraction(quiz("")));

        assertThat(built.profile().interests()).containsEntry("beaches", 1.0).containsEntry("nightlife", 0.0)
                .containsEntry("history", 0.5);
        assertThat(built.profile().pace()).isEqualTo("balanced");
        assertThat(built.profile().budget().food()).isEqualTo("low");
        assertThat(built.profile().dietary()).containsExactly("vegetarian");
        assertThat(built.profile().summary())
                .isEqualTo("Balanced-pace traveler drawn to beaches and history & heritage; prefers hidden gems over famous sights.");
        assertThat(built.inferences()).isEmpty();
    }

    @Test
    void rulesFindUnratedInterestsInFreeTextWithEvidence() {
        QuizAnswers q = quiz("I want to visit old churches, try surfing and see the beaches at sunset.");
        AiExtraction x = ProfileBuilder.ruleExtraction(q);

        assertThat(x.extraInterests()).extracting(ExtraInterest::tag)
                .containsExactlyInAnyOrder("architecture", "water_sports")
                // beaches is already rated in the quiz, so text never overrides it
                .doesNotContain("beaches");
        var built = ProfileBuilder.build(q, x);
        assertThat(built.profile().interests()).containsEntry("architecture", 0.6).containsEntry("beaches", 1.0);
        assertThat(built.inferences()).allSatisfy(i -> assertThat(q.idealTrip()).contains(i.evidence()));
    }

    @Test
    void keywordsMatchWholeWordsOnly() {
        AiExtraction x = ProfileBuilder.ruleExtraction(quiz("A great barbecue with a startup crowd"));
        assertThat(x.extraInterests()).extracting(ExtraInterest::tag).doesNotContain("art", "nightlife");
    }

    @Test
    void ungroundedOrRatedModelInterestsAreDropped() {
        QuizAnswers q = quiz("Quiet mornings with good coffee and a pottery class.");
        AiExtraction x = new AiExtraction(List.of(
                new ExtraInterest("cafes", 0.8, "good coffee"),
                new ExtraInterest("crafts", 0.7, "pottery class"),
                new ExtraInterest("nightlife", 0.9, "good coffee"),            // already rated → ignored
                new ExtraInterest("wellness", 0.6, "spa weekend"),             // quote not in text → ignored
                new ExtraInterest("casinos", 0.9, "pottery class")),           // not in vocabulary → ignored
                List.of("sea view", "Quiet room"), List.of(), "A calm traveler who likes coffee and crafts.");

        var built = ProfileBuilder.build(q, x);

        assertThat(built.profile().interests()).containsEntry("cafes", 0.8).containsEntry("crafts", 0.7)
                .containsEntry("nightlife", 0.0).doesNotContainKeys("wellness", "casinos");
        // explicit quiz answers win; AI additions are deduplicated case-insensitively
        assertThat(built.profile().mustHaves()).containsExactly("Sea view", "Quiet room");
        assertThat(built.profile().summary()).isEqualTo("A calm traveler who likes coffee and crafts.");
        assertThat(built.inferences()).extracting(ProfileModels.Inference::field)
                .containsExactly("interests.cafes", "interests.crafts", "mustHaves");
    }

    @Test
    void mentionedInterestsAreNeverWeightedZero() {
        QuizAnswers q = quiz("A pottery workshop with a local artisan.");
        AiExtraction x = new AiExtraction(List.of(new ExtraInterest("crafts", 0.0, "pottery workshop")), List.of(),
                List.of(), "Likes crafts.");
        assertThat(ProfileBuilder.build(q, x).profile().interests()).containsEntry("crafts", 0.3);
    }

    @Test
    void validatorReportsVocabularyAndRangeProblems() {
        AiExtraction bad = new AiExtraction(List.of(new ExtraInterest("casinos", 1.5, "")), List.of(), List.of(), "");
        assertThat(ProfileValidator.validateExtraction(bad)).containsExactly("summary must be a non-empty sentence");

        AiExtraction bad2 = new AiExtraction(List.of(new ExtraInterest("casinos", 1.5, "")), List.of(), List.of(), "ok");
        assertThat(ProfileValidator.validateExtraction(bad2)).hasSize(3);

        QuizAnswers wrong = new QuizAnswers(Map.of("beaches", 3), "zoom", new Budget("mid", "cheap", "high"), 50, 50,
                List.of("paleo"), List.of(), List.of(), List.of(), "");
        assertThat(ProfileValidator.validateQuiz(wrong))
                .containsExactly("Unknown pace: zoom", "Unknown food budget: cheap", "Unknown dietary need: paleo");
    }
}
