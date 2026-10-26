package com.meridian.decision;

import com.meridian.ai.AiCacheService;
import com.meridian.ai.AiClient;
import com.meridian.ai.AiException;
import com.meridian.ai.AiRequest;
import com.meridian.ai.AiResult;
import com.meridian.ai.PromptLibrary;
import com.meridian.ai.PromptTemplate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A6 — writes the decision-log sentence after a vote has closed. The model gets only the code-written facts (who
 * voted which way, the counts, the reason and the discussion), must state the count as given, and may not write a
 * number that is not in the facts. Results are cached per input; on any failure the template stays.
 */
@Component
public class DecisionSummaryWriter {

    static final String FEATURE = "decision-summary";
    static final String PROMPT = "decision-summary";
    static final int MAX = 280;

    private static final Logger log = LoggerFactory.getLogger(DecisionSummaryWriter.class);
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,:]\\d+)*");

    public record Output(String summary) {}

    public record Written(String summary, String model, String promptVersion) {}

    private final AiClient ai;
    private final AiCacheService cache;
    private final PromptLibrary prompts;

    public DecisionSummaryWriter(AiClient ai, AiCacheService cache, PromptLibrary prompts) {
        this.ai = ai;
        this.cache = cache;
        this.prompts = prompts;
    }

    public boolean aiEnabled() {
        return ai.enabled();
    }

    /** The AI summary, or empty when AI is off or its answer could not be used (the template then stays). */
    public Optional<Written> write(DecisionTexts.Context context, boolean regenerate) {
        if (!ai.enabled()) return Optional.empty();
        String facts = DecisionTexts.facts(context);
        PromptTemplate prompt = prompts.get(PROMPT);
        String key = cache.key(FEATURE, prompt.version(), ai.model(), facts);
        if (!regenerate) {
            Optional<Written> hit = cache.get(key, Written.class);
            if (hit.isPresent()) return hit;
        }
        try {
            AiResult<Output> result = ai.generate(new AiRequest<>(FEATURE, prompt, Map.of("facts", facts), schema(),
                    Output.class, o -> validate(o, facts, context.tally())));
            Written written = new Written(result.value().summary().strip(), result.model(), prompt.version());
            cache.put(key, FEATURE, prompt.version(), ai.model(), written);
            return Optional.of(written);
        } catch (AiException e) {
            if (e.reason() == AiException.Reason.CANCELLED) throw e;
            log.info("Decision summary AI fallback: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Schema checks plus: the count is stated as given, and every number comes from the facts. */
    static List<String> validate(Output o, String facts, Tally tally) {
        List<String> errors = new ArrayList<>();
        if (o == null || o.summary() == null || o.summary().isBlank()) return List.of("summary is empty");
        String s = o.summary().strip();
        if (s.length() > MAX) errors.add("summary is longer than " + MAX + " characters");
        if (DecisionTexts.countPhrases(tally).stream().noneMatch(s::contains)) {
            errors.add("state the result with the count from the facts, e.g. \"" + tally.approvals() + " of "
                    + tally.members() + "\"");
        }
        Set<String> allowed = numbers(facts);
        for (String n : numbers(s)) {
            if (!allowed.contains(n)) errors.add("the number " + n + " is not in the facts");
        }
        return errors;
    }

    static Set<String> numbers(String text) {
        Set<String> out = new HashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) out.add(m.group().replace(",", ""));
        return out;
    }

    static Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("summary"),
                "properties", Map.of("summary", Map.of("type", "string", "maxLength", MAX)));
    }
}
