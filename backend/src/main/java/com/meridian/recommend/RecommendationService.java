package com.meridian.recommend;

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
import com.meridian.fit.FitScorer.FitMember;
import com.meridian.fit.TouristTrapScorer;
import com.meridian.fit.TripGroupLoader;
import com.meridian.fit.TripGroupLoader.TripGroup;
import com.meridian.place.PlaceCategory;
import com.meridian.place.PlaceDtos.PlaceResults;
import com.meridian.place.PlaceService;
import com.meridian.profile.ProfileModels.Provenance;
import com.meridian.profile.Vocabulary;
import com.meridian.recommend.CandidateScorer.Candidate;
import com.meridian.recommend.RecommendationModels.Recommendation;
import com.meridian.recommend.RecommendationModels.RecommendationQuery;
import com.meridian.recommend.RecommendationModels.RecommendationSet;
import com.meridian.shortlist.ShortlistItem;
import com.meridian.shortlist.ShortlistItemRepository;
import com.meridian.trip.TripAccess;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * A2 — place recommendations (spec §5.5). Candidates come from the {@code PlacesClient} around the destination, code
 * scores them for the group, and the model re-ranks the top ten and writes "why this fits" in batches of five. The
 * model only ever sees short ids (c1…c10) that map to the supplied candidates. Results are cached per input, so a
 * cached set (e.g. the seeded demo) loads instantly; the rule-based ranking is always available.
 */
@Service
public class RecommendationService {

    static final String FEATURE = "recommend";
    static final String RERANK_PROMPT = "recommend-rerank";
    static final String EXPLAIN_PROMPT = "recommend-explain";
    /** Small-model rule: explanations in batches of at most five. */
    static final int EXPLAIN_BATCH = 5;
    /** How far the model may move a place from its code rank. */
    static final int MAX_MOVE = 3;
    static final int WHY_MAX = 180;

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");

    public record Rerank(List<String> ranking) {}

    public record Explanations(List<Item> items) {

        public record Item(String id, String why) {}
    }

    /** Cached per input hash: the AI's order and text, keyed by place id. */
    public record Cached(List<UUID> order, Map<UUID, String> why, String model, String promptVersion) {}

    interface Progress {
        void report(int percent, String message);
    }

    /** Everything one recommendation request needs, computed once. */
    record Prepared(RecommendationQuery query, int gems, int groupGems, List<Candidate> ranked, PlaceResults places,
            String groupText, String key) {

        List<Candidate> top() {
            return ranked.subList(0, Math.min(CandidateScorer.TOP, ranked.size()));
        }
    }

    private final TripAccess access;
    private final TripGroupLoader loader;
    private final PlaceService placeService;
    private final ShortlistItemRepository shortlist;
    private final TouristTrapScorer trapScorer;
    private final AiClient ai;
    private final AiCacheService cache;
    private final PromptLibrary prompts;
    private final AiJobService jobs;

    public RecommendationService(TripAccess access, TripGroupLoader loader, PlaceService placeService,
            ShortlistItemRepository shortlist, TouristTrapScorer trapScorer, AiClient ai, AiCacheService cache,
            PromptLibrary prompts, AiJobService jobs) {
        this.access = access;
        this.loader = loader;
        this.placeService = placeService;
        this.shortlist = shortlist;
        this.trapScorer = trapScorer;
        this.ai = ai;
        this.cache = cache;
        this.prompts = prompts;
        this.jobs = jobs;
    }

    /** Instant answer: the cached AI ranking for exactly these inputs if there is one, else the rule-based ranking. */
    public RecommendationSet get(UUID tripId, UUID userId, RecommendationQuery query) {
        Prepared p = prepare(tripId, userId, query);
        return cache.get(p.key(), Cached.class)
                .map(c -> aiSet(p, c))
                .orElseGet(() -> rulesSet(p, ai.enabled()
                        ? "Ranked by code from the group's profiles. Ask AI to re-rank and explain the top "
                                + CandidateScorer.TOP + "."
                        : "AI is turned off, so these were ranked and explained by rules."));
    }

    /** Re-rank and explain with AI in a background job; falls back to the rule-based set. */
    public AiJob startAi(UUID tripId, UUID userId, RecommendationQuery query) {
        Prepared p = prepare(tripId, userId, query);
        return jobs.submit(userId, tripId, FEATURE, ctx -> {
            ctx.progress(5, "Scored " + p.ranked().size() + " places from OpenStreetMap");
            RecommendationSet set = generate(p, query.regenerate(), ctx::progress);
            ctx.progress(100, "Suggestions ready");
            return set;
        });
    }

    Prepared prepare(UUID tripId, UUID userId, RecommendationQuery query) {
        if (query.category() == PlaceCategory.OTHER) throw ApiException.badRequest("Pick a category");
        access.requireMember(tripId, userId);
        TripGroup group = loader.load(tripId);
        List<FitMember> members = group.fitMembers();
        PlaceResults places = placeService.nearby(tripId, userId, query.category(), null);
        Set<UUID> onShortlist = shortlist.findByTripIdOrderByCreatedAtDesc(tripId).stream()
                .map(ShortlistItem::getPlaceId).filter(Objects::nonNull).collect(Collectors.toSet());
        int groupGems = CandidateScorer.groupGems(members);
        int gems = query.gems() != null ? query.gems() : groupGems;
        List<Candidate> ranked = CandidateScorer.rank(places.places(), onShortlist, members, trapScorer, gems,
                query.avoidCrowds(), group.trip().getDestinationLat(), group.trip().getDestinationLng());
        String groupText = groupText(members, gems, query.avoidCrowds());
        List<Map<String, Object>> keyed = ranked.stream().limit(CandidateScorer.TOP)
                .map(c -> Map.<String, Object>of("id", c.place().id().toString(), "score", c.score())).toList();
        String key = cache.key(FEATURE, promptVersion(), ai.model(), Map.of("category", query.category().name(),
                "gems", gems, "avoidCrowds", query.avoidCrowds(), "group", groupText, "candidates", keyed));
        return new Prepared(query, gems, groupGems, ranked, places, groupText, key);
    }

    RecommendationSet generate(Prepared p, boolean regenerate, Progress progress) {
        if (p.top().isEmpty()) return rulesSet(p, "No places to suggest in this category yet.");
        if (!regenerate) {
            Optional<Cached> hit = cache.get(p.key(), Cached.class);
            if (hit.isPresent()) return aiSet(p, hit.get());
        }
        try {
            if (!ai.enabled()) throw new AiException(AiException.Reason.DISABLED, "AI is disabled");
            List<Candidate> top = p.top();
            Map<String, Candidate> byShortId = new LinkedHashMap<>();
            IntStream.range(0, top.size()).forEach(i -> byShortId.put("c" + (i + 1), top.get(i)));
            List<String> ids = List.copyOf(byShortId.keySet());

            List<String> order = ids;
            String model = ai.model();
            if (ids.size() > 1) {
                progress.report(15, "Asking " + ai.model() + " to re-rank the top " + ids.size());
                PromptTemplate rerank = prompts.get(RERANK_PROMPT);
                AiResult<Rerank> ranked = ai.generate(new AiRequest<>(FEATURE + "-rerank", rerank,
                        Map.of("group", p.groupText(), "candidates", rerankLines(byShortId)), rerankSchema(ids),
                        Rerank.class, r -> validateRanking(r, ids)));
                order = boundMoves(ranked.value().ranking(), ids);
                model = ranked.model();
            }

            PromptTemplate explain = prompts.get(EXPLAIN_PROMPT);
            Map<UUID, String> why = new HashMap<>();
            int batches = (order.size() + EXPLAIN_BATCH - 1) / EXPLAIN_BATCH;
            for (int b = 0; b < batches; b++) {
                List<String> batch = order.subList(b * EXPLAIN_BATCH, Math.min(order.size(), (b + 1) * EXPLAIN_BATCH));
                progress.report(45 + 45 * b / batches, "Writing why each place fits (" + (b + 1) + "/" + batches + ")");
                Map<String, String> facts = new LinkedHashMap<>();
                batch.forEach(id -> facts.put(id, explainLine(id, byShortId.get(id))));
                // the explain step gets only the settings, not the group's likes, which it would pad sentences with
                String settings = settingsText(p.groupText());
                AiResult<Explanations> result = ai.generate(new AiRequest<>(FEATURE + "-explain", explain,
                        Map.of("group", settings, "candidates", String.join("\n", facts.values())),
                        explainSchema(batch), Explanations.class, e -> validateExplanations(e, facts, settings)));
                for (Explanations.Item i : result.value().items()) {
                    Candidate c = byShortId.get(i.id());
                    List<String> claimed = unoffered(i.why(), facts.get(i.id()));
                    if (claimed.isEmpty()) {
                        why.put(c.place().id(), i.why().strip());
                    } else {
                        log.info("Replacing AI why for {}: claims {}", c.place().name(), claimed);
                        why.put(c.place().id(), c.rulesWhy());
                    }
                }
            }
            Cached value = new Cached(order.stream().map(id -> byShortId.get(id).place().id()).toList(), why, model,
                    promptVersion());
            cache.put(p.key(), FEATURE, promptVersion(), ai.model(), value);
            return aiSet(p, value);
        } catch (AiException e) {
            if (e.reason() == AiException.Reason.CANCELLED) throw e;
            log.info("Recommendation AI fallback: {}", e.getMessage());
            return rulesSet(p, e.userMessage());
        }
    }

    private String promptVersion() {
        return prompts.get(RERANK_PROMPT).version() + "+" + prompts.get(EXPLAIN_PROMPT).version();
    }

    private RecommendationSet rulesSet(Prepared p, String note) {
        List<Candidate> top = p.top();
        List<Recommendation> items = IntStream.range(0, top.size())
                .mapToObj(i -> view(top.get(i), i + 1, i + 1, top.get(i).rulesWhy())).toList();
        return set(p, items, new Provenance("rules", null, null, List.of(), note));
    }

    private RecommendationSet aiSet(Prepared p, Cached c) {
        Map<UUID, Integer> rulesRank = new HashMap<>();
        List<Candidate> top = p.top();
        for (int i = 0; i < top.size(); i++) rulesRank.put(top.get(i).place().id(), i + 1);
        Map<UUID, Candidate> byId = top.stream().collect(Collectors.toMap(x -> x.place().id(), x -> x));
        List<Recommendation> items = new ArrayList<>();
        for (UUID id : c.order()) {
            Candidate candidate = byId.get(id);
            if (candidate == null) continue;
            items.add(view(candidate, items.size() + 1, rulesRank.get(id), c.why().getOrDefault(id,
                    candidate.rulesWhy())));
        }
        return set(p, items, new Provenance("ai", c.model(), c.promptVersion(), List.of(),
                "Code scored " + p.ranked().size() + " places against everyone's profile; the AI re-ranked the top "
                        + items.size() + " and wrote the explanations from those facts."));
    }

    private RecommendationSet set(Prepared p, List<Recommendation> items, Provenance provenance) {
        return new RecommendationSet(p.query().category(), p.gems(), p.groupGems(), p.query().avoidCrowds(), items,
                p.ranked().size(), p.places().source(), p.places().fetchedAt(), p.places().stale(), provenance,
                p.key(), ai.enabled());
    }

    private static Recommendation view(Candidate c, int rank, int rulesRank, String why) {
        return new Recommendation(c.place(), rank, rulesRank, c.score(), why, c.matchedInterests(), c.fits(),
                c.misses(), c.localSignals(), c.distanceKm(), false);
    }

    // ---- prompt input ----------------------------------------------------------------------------------------

    static String groupText(List<FitMember> members, int gems, boolean avoidCrowds) {
        List<FitMember> profiled = members.stream().filter(m -> m.profile() != null).toList();
        StringBuilder s = new StringBuilder(profiled.size() + (profiled.size() == 1 ? " traveler" : " travelers"))
                .append(" with preferences.");
        if (!profiled.isEmpty()) {
            List<String> all = new ArrayList<>();
            List<String> some = new ArrayList<>();
            for (Vocabulary.Interest interest : Vocabulary.INTERESTS) {
                List<String> fans = profiled.stream()
                        .filter(m -> m.profile().interests().getOrDefault(interest.tag(), 0.0) >= 0.6)
                        .map(FitMember::displayName).toList();
                String label = interest.label().toLowerCase(Locale.ROOT);
                if (fans.size() == profiled.size()) all.add(label);
                else if (!fans.isEmpty() && some.size() < 5) some.add(label + " (" + String.join(", ", fans) + ")");
            }
            if (!all.isEmpty()) s.append(" Liked by everyone: ").append(String.join(", ", all)).append('.');
            if (!some.isEmpty()) s.append(" Also liked: ").append(String.join(", ", some)).append('.');
            List<String> never = profiled.stream().flatMap(m -> m.profile().dealbreakers().stream()
                    .map(d -> d + " (" + m.displayName() + ")")).limit(4).toList();
            if (!never.isEmpty()) s.append(" Dealbreakers: ").append(String.join(", ", never)).append('.');
        }
        s.append(" Hidden-gems setting: ").append(gems).append("/100 (")
                .append(gems >= 60 ? "prefers lesser-known places" : gems <= 40 ? "prefers well-known classics"
                        : "a mix of both")
                .append(").");
        s.append(" Avoid crowds: ").append(avoidCrowds ? "yes" : "no").append('.');
        return s.toString();
    }

    private static String rerankLines(Map<String, Candidate> byShortId) {
        return byShortId.entrySet().stream().map(e -> {
            Candidate c = e.getValue();
            return e.getKey() + " | " + c.place().name() + " | " + offers(c) + " | "
                    + (c.localSignals().isEmpty() ? "none" : String.join("; ", c.localSignals())) + " | " + c.score();
        }).collect(Collectors.joining("\n"));
    }

    static String explainLine(String id, Candidate c) {
        StringBuilder s = new StringBuilder(id).append(" | ").append(c.place().name()).append(" | offers: ")
                .append(offers(c));
        if (!c.fits().isEmpty()) s.append(" | suits: ").append(String.join(", ", c.fits()));
        if (!c.misses().isEmpty()) s.append(" | not for: ").append(String.join(", ", c.misses()));
        if (!c.localSignals().isEmpty()) s.append(" | local signals: ").append(String.join("; ", c.localSignals()));
        return s.toString();
    }

    private static String offers(Candidate c) {
        if (!c.matchedInterests().isEmpty()) return String.join(", ", c.matchedInterests());
        Map<String, String> labels = Vocabulary.interestLabels();
        List<String> any = c.signals().interests().entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(2).map(e -> labels.get(e.getKey()).toLowerCase(Locale.ROOT)).toList();
        return any.isEmpty() ? c.place().category().name().toLowerCase(Locale.ROOT) : String.join(", ", any);
    }

    // ---- schemas and validation ------------------------------------------------------------------------------

    static Map<String, Object> rerankSchema(List<String> ids) {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("ranking"),
                "properties", Map.of("ranking", Map.of("type", "array", "minItems", ids.size(), "maxItems", ids.size(),
                        "items", Map.of("type", "string", "enum", ids))));
    }

    /** The ranking must be a permutation of exactly the supplied ids. */
    static List<String> validateRanking(Rerank r, List<String> ids) {
        List<String> errors = new ArrayList<>();
        if (r == null || r.ranking() == null) return List.of("ranking is missing");
        Set<String> seen = new HashSet<>();
        for (String id : r.ranking()) {
            if (!ids.contains(id)) errors.add("unknown id \"" + id + "\"");
            else if (!seen.add(id)) errors.add("id \"" + id + "\" appears twice");
        }
        ids.stream().filter(id -> !seen.contains(id)).forEach(id -> errors.add("missing id \"" + id + "\""));
        return errors;
    }

    /**
     * Code keeps the final say on order: the model's ranking is repaired so no place moves more than
     * {@link #MAX_MOVE} positions from its code rank (ties go to the code's order). A small model that merely
     * rotates the list can't bury the best-scoring place.
     */
    static List<String> boundMoves(List<String> aiOrder, List<String> codeOrder) {
        Map<String, Integer> aiPos = new HashMap<>();
        for (int i = 0; i < aiOrder.size(); i++) aiPos.put(aiOrder.get(i), i);
        return codeOrder.stream()
                .sorted(Comparator.<String>comparingInt(id -> {
                    int code = codeOrder.indexOf(id);
                    return Math.clamp(aiPos.get(id), code - MAX_MOVE, code + MAX_MOVE);
                }).thenComparingInt(codeOrder::indexOf))
                .toList();
    }

    static Map<String, Object> explainSchema(List<String> ids) {
        Map<String, Object> item = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("id", "why"),
                "properties", Map.of(
                        "id", Map.of("type", "string", "enum", ids),
                        "why", Map.of("type", "string", "maxLength", WHY_MAX)));
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("items"),
                "properties", Map.of("items", Map.of("type", "array", "minItems", ids.size(), "maxItems", ids.size(),
                        "items", item)));
    }

    /** Exactly the batch's ids, a sentence each, and no number that isn't in that place's facts or the group line. */
    static List<String> validateExplanations(Explanations e, Map<String, String> facts, String groupText) {
        List<String> errors = new ArrayList<>();
        if (e == null || e.items() == null) return List.of("items is missing");
        Set<String> seen = new HashSet<>();
        for (Explanations.Item item : e.items()) {
            if (item == null || item.id() == null || !facts.containsKey(item.id())) {
                errors.add("unknown id \"" + (item == null ? null : item.id()) + "\"");
                continue;
            }
            if (!seen.add(item.id())) errors.add("id \"" + item.id() + "\" appears twice");
            if (item.why() == null || item.why().isBlank()) {
                errors.add(item.id() + ": why is empty");
                continue;
            }
            if (item.why().length() > WHY_MAX) errors.add(item.id() + ": why is longer than " + WHY_MAX + " characters");
            // the line starts with the short id ("c3 | ..."), whose digit is not a fact
            Set<String> allowed = numbers(facts.get(item.id()).substring(item.id().length()) + " " + groupText);
            for (String n : numbers(item.why())) {
                if (!allowed.contains(n)) errors.add(item.id() + ": the number " + n + " is not in the facts");
            }
        }
        facts.keySet().stream().filter(id -> !seen.contains(id))
                .forEach(id -> errors.add("missing item for id \"" + id + "\""));
        return errors;
    }

    /**
     * Interests a sentence claims that the place's own facts don't mention. A small model asked twice still pads
     * ("a quiet beach with local food"), so such a sentence is swapped for the template rather than retried.
     */
    static List<String> unoffered(String why, String factsLine) {
        List<String> offered = Vocabulary.interestsMentioned(factsLine);
        Map<String, String> labels = Vocabulary.interestLabels();
        return Vocabulary.interestsMentioned(why).stream().filter(tag -> !offered.contains(tag))
                .map(tag -> labels.get(tag).toLowerCase(Locale.ROOT)).toList();
    }

    /** The group line without who likes what: the traveler count and the slider and crowd settings. */
    static String settingsText(String groupText) {
        return Arrays.stream(groupText.split("(?<=\\.) "))
                .filter(sentence -> !sentence.startsWith("Liked by everyone:") && !sentence.startsWith("Also liked:")
                        && !sentence.startsWith("Dealbreakers:"))
                .collect(Collectors.joining(" "));
    }

    private static Set<String> numbers(String text) {
        Set<String> out = new HashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) out.add(m.group().replace(",", ""));
        return out;
    }
}
