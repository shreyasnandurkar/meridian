package com.meridian.fit;

import com.meridian.ai.AiCacheService;
import com.meridian.ai.AiClient;
import com.meridian.ai.AiException;
import com.meridian.ai.AiRequest;
import com.meridian.ai.AiResult;
import com.meridian.ai.PromptLibrary;
import com.meridian.ai.PromptTemplate;
import com.meridian.fit.GroupFitModels.Row;
import com.meridian.fit.GroupFitModels.Status;
import com.meridian.fit.GroupFitModels.Tradeoff;
import com.meridian.fit.GroupFitModels.TradeoffSummary;
import com.meridian.profile.ProfileModels.Provenance;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A3 — phrases the code-computed trade-offs in plain language. The model sees only the facts of rows that are not
 * agreements (never private budget values), may only answer for the keys it was given, and may not write a number
 * that is not in the facts. Results are cached per input; the template text is the fallback.
 */
@Component
public class TradeoffWriter {

    static final String FEATURE = "group-tradeoffs";
    static final String PROMPT = "group-tradeoffs";
    /** Small-model rule: at most five trade-offs per call. */
    static final int BATCH = 5;

    private static final Logger log = LoggerFactory.getLogger(TradeoffWriter.class);
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");

    /** What the model returns. */
    public record Output(List<Item> items) {

        public record Item(String key, String headline, String explanation, String suggestion) {}
    }

    /** Cached per input hash. */
    public record Cached(List<Tradeoff> items, String model, String promptVersion) {}

    public interface Progress {
        void report(int percent, String message);
    }

    private final AiClient ai;
    private final AiCacheService cache;
    private final PromptLibrary prompts;

    public TradeoffWriter(AiClient ai, AiCacheService cache, PromptLibrary prompts) {
        this.ai = ai;
        this.cache = cache;
        this.prompts = prompts;
    }

    public boolean aiEnabled() {
        return ai.enabled();
    }

    /** Rows worth explaining: everything that is not agreement or missing data. */
    static List<Row> worthPhrasing(List<Row> rows) {
        return rows.stream()
                .filter(r -> r.status() == Status.TRADE_OFF || r.status() == Status.CONFLICT
                        || r.status() == Status.PLAN_AROUND)
                .toList();
    }

    /** Cache key for the current trade-offs; the same facts always give the same key. */
    public String inputKey(List<Row> rows) {
        List<Map<String, Object>> input = worthPhrasing(rows).stream()
                .map(r -> Map.<String, Object>of("key", r.key(), "status", r.status().name(), "facts", r.facts(),
                        "suggestion", r.suggestion() == null ? "" : r.suggestion()))
                .toList();
        return cache.key(FEATURE, prompts.get(PROMPT).version(), ai.model(), input);
    }

    /** The cached AI text for these rows, or the template text, without calling the model. */
    public TradeoffSummary current(List<Row> rows) {
        String key = inputKey(rows);
        return cache.get(key, Cached.class)
                .map(c -> aiSummary(c, key))
                .orElseGet(() -> template(rows, key, ai.enabled()
                        ? "Written from templates. Ask AI to phrase these trade-offs in plain language."
                        : "AI is turned off, so these were written from templates."));
    }

    /** Calls the model (unless cached and not regenerating); falls back to templates on any AI failure. */
    public TradeoffSummary generate(List<Row> rows, boolean regenerate, Progress progress) {
        List<Row> todo = worthPhrasing(rows);
        String key = inputKey(rows);
        if (todo.isEmpty()) return template(rows, key, "Nothing to reconcile: the group agrees on everything known.");
        if (!regenerate) {
            Optional<Cached> hit = cache.get(key, Cached.class);
            if (hit.isPresent()) return aiSummary(hit.get(), key);
        }
        try {
            if (!ai.enabled()) throw new AiException(AiException.Reason.DISABLED, "AI is disabled");
            PromptTemplate prompt = prompts.get(PROMPT);
            List<Tradeoff> out = new ArrayList<>();
            String model = null;
            int batches = (todo.size() + BATCH - 1) / BATCH;
            for (int b = 0; b < batches; b++) {
                List<Row> batch = todo.subList(b * BATCH, Math.min(todo.size(), (b + 1) * BATCH));
                progress.report(20 + 70 * b / batches,
                        "Asking " + ai.model() + " to explain " + batch.size() + " trade-offs");
                AiResult<Output> result = ai.generate(new AiRequest<>(FEATURE, prompt,
                        Map.of("tradeoffs", render(batch)), schema(batch), Output.class, o -> validate(o, batch)));
                model = result.model();
                Map<String, Output.Item> byKey = result.value().items().stream()
                        .collect(Collectors.toMap(Output.Item::key, i -> i, (a, c) -> a));
                for (Row row : batch) {
                    Output.Item item = byKey.get(row.key());
                    out.add(new Tradeoff(row.key(), item.headline().strip(), item.explanation().strip(),
                            item.suggestion().strip()));
                }
            }
            Cached value = new Cached(out, model, prompt.version());
            cache.put(key, FEATURE, prompt.version(), ai.model(), value);
            return aiSummary(value, key);
        } catch (AiException e) {
            if (e.reason() == AiException.Reason.CANCELLED) throw e;
            log.info("Trade-off AI fallback: {}", e.getMessage());
            return template(rows, key, e.userMessage());
        }
    }

    private static TradeoffSummary aiSummary(Cached c, String key) {
        return new TradeoffSummary(c.items(), new Provenance("ai", c.model(), c.promptVersion(), List.of(),
                "Who wants what was computed by code; the AI only put it into words."), key);
    }

    static TradeoffSummary template(List<Row> rows, String key, String note) {
        List<Tradeoff> items = worthPhrasing(rows).stream()
                .map(r -> new Tradeoff(r.key(), r.label() + ": " + statusWord(r.status()), r.summary(),
                        r.suggestion()))
                .toList();
        return new TradeoffSummary(items, new Provenance("rules", null, null, List.of(), note), key);
    }

    private static String statusWord(Status s) {
        return switch (s) {
            case CONFLICT -> "conflict";
            case TRADE_OFF -> "trade-off";
            case PLAN_AROUND -> "plan around";
            default -> s.name().toLowerCase(Locale.ROOT);
        };
    }

    static String render(List<Row> batch) {
        return batch.stream().map(r -> "[" + r.key() + "] " + r.label() + " (" + statusWord(r.status()) + ")\n"
                        + "facts: " + String.join(" | ", r.facts())
                        + (r.suggestion() == null ? "" : "\ncode suggestion: " + r.suggestion()))
                .collect(Collectors.joining("\n\n"));
    }

    /** Server-side checks on top of the schema: right keys, sane lengths, no numbers that aren't in the facts. */
    static List<String> validate(Output o, List<Row> batch) {
        List<String> errors = new ArrayList<>();
        if (o == null || o.items() == null) return List.of("items is missing");
        Set<String> expected = batch.stream().map(Row::key).collect(Collectors.toSet());
        Set<String> seen = new HashSet<>();
        for (Output.Item item : o.items()) {
            if (item == null || item.key() == null) {
                errors.add("every item needs a key");
                continue;
            }
            if (!expected.contains(item.key())) errors.add("unknown key \"" + item.key() + "\"");
            else if (!seen.add(item.key())) errors.add("key \"" + item.key() + "\" appears twice");
            check(errors, item.key(), "headline", item.headline(), 80);
            check(errors, item.key(), "explanation", item.explanation(), 300);
            check(errors, item.key(), "suggestion", item.suggestion(), 200);
            Row row = batch.stream().filter(r -> r.key().equals(item.key())).findFirst().orElse(null);
            if (row == null) continue;
            String source = String.join(" ", row.facts()) + " " + (row.suggestion() == null ? "" : row.suggestion());
            Set<String> allowed = numbers(source);
            for (String text : new String[] {item.headline(), item.explanation(), item.suggestion()}) {
                for (String n : numbers(text)) {
                    if (!allowed.contains(n)) errors.add(item.key() + ": the number " + n + " is not in the facts");
                }
            }
        }
        expected.stream().filter(k -> !seen.contains(k)).sorted()
                .forEach(k -> errors.add("missing item for key \"" + k + "\""));
        return errors;
    }

    private static void check(List<String> errors, String key, String field, String value, int max) {
        if (value == null || value.isBlank()) errors.add(key + ": " + field + " is empty");
        else if (value.length() > max) errors.add(key + ": " + field + " is longer than " + max + " characters");
    }

    static Set<String> numbers(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) return out;
        Matcher m = NUMBER.matcher(text);
        while (m.find()) out.add(m.group().replace(",", ""));
        return out;
    }

    static Map<String, Object> schema(List<Row> batch) {
        Map<String, Object> item = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("key", "headline", "explanation", "suggestion"),
                "properties", Map.of(
                        "key", Map.of("type", "string", "enum", batch.stream().map(Row::key).toList()),
                        "headline", Map.of("type", "string", "maxLength", 80),
                        "explanation", Map.of("type", "string", "maxLength", 300),
                        "suggestion", Map.of("type", "string", "maxLength", 200)));
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("items"),
                "properties", Map.of("items", Map.of("type", "array", "minItems", batch.size(),
                        "maxItems", batch.size(), "items", item)));
    }
}
