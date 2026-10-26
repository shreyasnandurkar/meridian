package com.meridian.profile;

import com.meridian.profile.ProfileModels.AiExtraction;
import com.meridian.profile.ProfileModels.Budget;
import com.meridian.profile.ProfileModels.QuizAnswers;
import com.meridian.profile.ProfileModels.TravelProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Vocabulary and range checks shared by quiz input, user edits and model output. */
public final class ProfileValidator {

    static final int MAX_EXTRA_INTERESTS = 6;
    static final int MAX_AI_LIST_ITEMS = 5;

    private ProfileValidator() {}

    public static List<String> validateQuiz(QuizAnswers q) {
        List<String> errors = new ArrayList<>();
        q.interestRatings().forEach((tag, rating) -> {
            if (!Vocabulary.INTEREST_TAGS.contains(tag)) errors.add("Unknown interest: " + tag);
        });
        checkCommon(errors, q.pace(), q.budget(), q.dietary(), q.accessNeeds());
        return errors;
    }

    public static List<String> validateProfile(TravelProfile p) {
        List<String> errors = new ArrayList<>();
        checkInterests(errors, p.interests());
        checkCommon(errors, p.pace(), p.budget(), p.dietary(), p.accessNeeds());
        return errors;
    }

    /** Model output checks. Messages are written so the model can fix them on retry. */
    public static List<String> validateExtraction(AiExtraction x) {
        List<String> errors = new ArrayList<>();
        if (x.extraInterests() == null) errors.add("extraInterests is required (use [] if none)");
        if (x.mustHaves() == null) errors.add("mustHaves is required (use [] if none)");
        if (x.dealbreakers() == null) errors.add("dealbreakers is required (use [] if none)");
        if (x.summary() == null || x.summary().isBlank()) errors.add("summary must be a non-empty sentence");
        else if (x.summary().length() > 240) errors.add("summary must be at most 240 characters");
        if (!errors.isEmpty()) return errors;

        if (x.extraInterests().size() > MAX_EXTRA_INTERESTS) {
            errors.add("extraInterests may contain at most " + MAX_EXTRA_INTERESTS + " items");
        }
        for (AiExtraction.ExtraInterest i : x.extraInterests()) {
            if (!Vocabulary.INTEREST_TAGS.contains(i.tag())) {
                errors.add("extraInterests tag '" + i.tag() + "' is not one of the allowed tags");
            }
            if (i.weight() < 0 || i.weight() > 1) errors.add("weight for '" + i.tag() + "' must be between 0 and 1");
            if (i.evidence() == null || i.evidence().isBlank() || i.evidence().length() > 120) {
                errors.add("evidence for '" + i.tag() + "' must be a short quote (1-120 characters)");
            }
        }
        checkShortList(errors, "mustHaves", x.mustHaves());
        checkShortList(errors, "dealbreakers", x.dealbreakers());
        return errors;
    }

    private static void checkShortList(List<String> errors, String field, List<String> items) {
        if (items.size() > MAX_AI_LIST_ITEMS) errors.add(field + " may contain at most " + MAX_AI_LIST_ITEMS + " items");
        for (String item : items) {
            if (item == null || item.isBlank() || item.length() > 80) {
                errors.add(field + " items must be short phrases (1-80 characters)");
                return;
            }
        }
    }

    private static void checkInterests(List<String> errors, Map<String, Double> interests) {
        interests.forEach((tag, weight) -> {
            if (!Vocabulary.INTEREST_TAGS.contains(tag)) errors.add("Unknown interest: " + tag);
            if (weight == null || weight < 0 || weight > 1) errors.add("Interest weight for " + tag + " must be 0-1");
        });
    }

    private static void checkCommon(List<String> errors, String pace, Budget budget, List<String> dietary,
            List<String> access) {
        if (!Vocabulary.PACE_IDS.contains(pace)) errors.add("Unknown pace: " + pace);
        if (budget != null) {
            checkIn(errors, "stay budget", budget.stay(), Vocabulary.BUDGET_IDS);
            checkIn(errors, "food budget", budget.food(), Vocabulary.BUDGET_IDS);
            checkIn(errors, "activities budget", budget.activities(), Vocabulary.BUDGET_IDS);
        }
        dietary.forEach(d -> checkIn(errors, "dietary need", d, Vocabulary.DIETARY_IDS));
        access.forEach(a -> checkIn(errors, "access need", a, Vocabulary.ACCESS_IDS));
    }

    private static void checkIn(List<String> errors, String what, String value, Set<String> allowed) {
        if (!allowed.contains(value)) errors.add("Unknown " + what + ": " + value);
    }
}
