package com.meridian.ai;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * One structured model call.
 *
 * @param feature   short id used for logging and caching, e.g. {@code profile}
 * @param prompt    versioned prompt template
 * @param vars      placeholder values for the template
 * @param schema    JSON schema sent as {@code response_format}
 * @param type      Java type the JSON output is bound to
 * @param validator returns human-readable problems; empty means valid. Problems are fed back to the model on retry.
 */
public record AiRequest<T>(
        String feature,
        PromptTemplate prompt,
        Map<String, String> vars,
        Map<String, Object> schema,
        Class<T> type,
        Function<T, List<String>> validator) {}
