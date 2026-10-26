package com.meridian.profile;

import com.meridian.ai.AiCacheService;
import com.meridian.ai.AiClient;
import com.meridian.ai.AiException;
import com.meridian.ai.AiRequest;
import com.meridian.ai.AiResult;
import com.meridian.ai.PromptLibrary;
import com.meridian.ai.PromptTemplate;
import com.meridian.ai.job.AiJob;
import com.meridian.ai.job.AiJobService;
import com.meridian.common.ApiException;
import com.meridian.profile.ProfileModels.AiExtraction;
import com.meridian.profile.ProfileModels.Provenance;
import com.meridian.profile.ProfileModels.QuizAnswers;
import com.meridian.profile.ProfileModels.TravelProfile;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A1 — travel-style profile from the onboarding quiz, with a rule-based fallback. */
@Service
public class ProfileService {

    static final String FEATURE = "profile";
    static final String PROMPT = "profile-extract";

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    public record ProfileView(TravelProfile profile, Provenance provenance, QuizAnswers quiz, boolean edited,
            Instant updatedAt) {

        static ProfileView from(TravelerProfileEntity e) {
            return new ProfileView(e.getProfile(), e.getProvenance(), e.getQuiz(), e.isEdited(), e.getUpdatedAt());
        }
    }

    /** What is cached per quiz hash: the validated extraction and where it came from. */
    public record CachedExtraction(AiExtraction extraction, String model, String promptVersion) {}

    private final TravelerProfileRepository profiles;
    private final AiClient ai;
    private final AiCacheService cache;
    private final PromptLibrary prompts;
    private final AiJobService jobs;

    public ProfileService(TravelerProfileRepository profiles, AiClient ai, AiCacheService cache, PromptLibrary prompts,
            AiJobService jobs) {
        this.profiles = profiles;
        this.ai = ai;
        this.cache = cache;
        this.prompts = prompts;
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public Optional<ProfileView> get(UUID userId) {
        return profiles.findById(userId).map(ProfileView::from);
    }

    /** Validates the quiz, then builds and saves the profile in an async job. */
    public AiJob startQuiz(UUID userId, QuizAnswers quiz) {
        List<String> errors = ProfileValidator.validateQuiz(quiz);
        if (!errors.isEmpty()) throw ApiException.invalid("Some quiz answers are not valid", errors);
        return jobs.submit(userId, FEATURE, ctx -> {
            ctx.progress(10, "Reading your answers");
            ProfileView view = generateAndSave(userId, quiz, ctx::progress);
            ctx.progress(100, "Profile ready");
            return view;
        });
    }

    interface Progress {
        void report(int percent, String message);
    }

    ProfileView generateAndSave(UUID userId, QuizAnswers quiz, Progress progress) {
        Provenance provenance;
        AiExtraction extraction;
        if (quiz.idealTrip().isBlank()) {
            extraction = ProfileBuilder.ruleExtraction(quiz);
            provenance = new Provenance("rules", null, null, List.of(),
                    "Built from your quiz answers. Describe your ideal trip to let AI pick up more.");
        } else {
            try {
                if (!ai.enabled()) throw new AiException(AiException.Reason.DISABLED, "AI is disabled");
                progress.report(30, "Asking " + ai.model() + " to read your description");
                CachedExtraction result = extractWithAi(quiz);
                extraction = result.extraction();
                provenance = new Provenance("ai", result.model(), result.promptVersion(), List.of(),
                        "Quiz answers were used as given; the AI only read your free-text description.");
            } catch (AiException e) {
                if (e.reason() == AiException.Reason.CANCELLED) throw e;
                log.info("Profile AI fallback: {}", e.getMessage());
                progress.report(60, "Using rule-based fallback");
                extraction = ProfileBuilder.ruleExtraction(quiz);
                provenance = new Provenance("rules", null, null, List.of(), e.userMessage());
            }
        }
        progress.report(85, "Saving your profile");
        ProfileBuilder.Built built = ProfileBuilder.build(quiz, extraction);
        provenance = new Provenance(provenance.source(), provenance.model(), provenance.promptVersion(),
                built.inferences(), provenance.note());
        return save(userId, built.profile(), quiz, provenance);
    }

    private CachedExtraction extractWithAi(QuizAnswers quiz) {
        PromptTemplate prompt = prompts.get(PROMPT);
        String key = cache.key(FEATURE, prompt.version(), ai.model(),
                Map.of("ratings", quiz.interestRatings(), "text", quiz.idealTrip()));
        Optional<CachedExtraction> hit = cache.get(key, CachedExtraction.class);
        if (hit.isPresent()) return hit.get();

        Map<String, String> vars = Map.of(
                "tags", String.join(", ", Vocabulary.INTERESTS.stream().map(Vocabulary.Interest::tag).toList()),
                "rated", quiz.interestRatings().isEmpty() ? "none"
                        : quiz.interestRatings().keySet().stream().sorted().collect(Collectors.joining(", ")),
                "description", quiz.idealTrip().replace("\"", "'"));
        AiResult<AiExtraction> result = ai.generate(new AiRequest<>(FEATURE, prompt, vars, extractionSchema(),
                AiExtraction.class, ProfileValidator::validateExtraction));
        CachedExtraction value = new CachedExtraction(result.value(), result.model(), result.promptVersion());
        cache.put(key, FEATURE, prompt.version(), ai.model(), value);
        return value;
    }

    private ProfileView save(UUID userId, TravelProfile profile, QuizAnswers quiz, Provenance provenance) {
        TravelerProfileEntity entity = profiles.findById(userId).orElseGet(() -> new TravelerProfileEntity(userId));
        entity.applyGenerated(profile, quiz, provenance);
        return ProfileView.from(profiles.save(entity));
    }

    @Transactional
    public ProfileView update(UUID userId, TravelProfile profile) {
        List<String> errors = ProfileValidator.validateProfile(profile);
        if (!errors.isEmpty()) throw ApiException.invalid("Some profile fields are not valid", errors);
        TravelerProfileEntity entity = profiles.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Take the quiz first to create your profile"));
        entity.applyEdit(profile);
        return ProfileView.from(profiles.save(entity));
    }

    /** JSON schema for {@link AiExtraction}, in the strict form OpenAI-compatible hosts accept. */
    static Map<String, Object> extractionSchema() {
        Map<String, Object> extraInterest = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("tag", "weight", "evidence"),
                "properties", Map.of(
                        "tag", Map.of("type", "string", "enum", Vocabulary.INTERESTS.stream()
                                .map(Vocabulary.Interest::tag).toList()),
                        "weight", Map.of("type", "number", "minimum", 0, "maximum", 1),
                        "evidence", Map.of("type", "string", "maxLength", 120)));
        Map<String, Object> shortList = Map.of(
                "type", "array", "maxItems", ProfileValidator.MAX_AI_LIST_ITEMS,
                "items", Map.of("type", "string", "maxLength", 80));
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("extraInterests", "mustHaves", "dealbreakers", "summary"),
                "properties", Map.of(
                        "extraInterests", Map.of("type", "array", "maxItems", ProfileValidator.MAX_EXTRA_INTERESTS,
                                "items", extraInterest),
                        "mustHaves", shortList,
                        "dealbreakers", shortList,
                        "summary", Map.of("type", "string", "maxLength", 240)));
    }
}
