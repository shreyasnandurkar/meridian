package com.meridian.ai;

/**
 * Single entry point to the language model. Implementations call an OpenAI-compatible
 * {@code /chat/completions} endpoint with JSON-schema constrained output, validate the result and retry
 * invalid output at most twice. Callers always catch {@link AiException} and fall back to deterministic code.
 */
public interface AiClient {

    boolean enabled();

    String model();

    <T> AiResult<T> generate(AiRequest<T> request);
}
