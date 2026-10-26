package com.meridian.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.ai.AiException.Reason;
import com.meridian.ai.PromptTemplate.Message;
import com.meridian.config.MeridianProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@link AiClient} for any OpenAI-compatible host (Ollama by default). */
public class OpenAiCompatibleAiClient implements AiClient {

    /** First try plus at most two retries (spec §4). */
    static final int MAX_ATTEMPTS = 3;

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAiClient.class);
    private static final Pattern THINK = Pattern.compile("(?s)<think>.*?</think>");
    private static final Pattern FENCE = Pattern.compile("(?s)^```(?:json)?\\s*(.*?)\\s*```$");

    private final MeridianProperties.Ai config;
    private final String baseUrl;
    private final ObjectMapper json;
    private final AiCallLogRepository callLog;
    private final HttpClient http;
    private final Semaphore permits;

    public OpenAiCompatibleAiClient(MeridianProperties.Ai config, ObjectMapper json, AiCallLogRepository callLog) {
        this.config = config;
        this.baseUrl = config.baseUrl().replaceAll("/+$", "");
        this.json = json;
        this.callLog = callLog;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.permits = new Semaphore(Math.max(1, config.maxConcurrency()), true);
    }

    @Override
    public boolean enabled() {
        return config.enabled();
    }

    @Override
    public String model() {
        return config.model();
    }

    @Override
    public <T> AiResult<T> generate(AiRequest<T> req) {
        if (!config.enabled()) {
            throw new AiException(Reason.DISABLED, "AI is disabled");
        }
        List<Message> messages = new ArrayList<>(req.prompt().render(req.vars()));
        String inputHash = Hashing.sha256(req.feature() + '|' + req.prompt().version() + '|' + config.model() + '|'
                + messages.getLast().content());
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException(Reason.CANCELLED, "Cancelled while waiting for the model");
        }
        try {
            long started = System.nanoTime();
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                Completion c = call(req, messages, attempt, inputHash);
                List<String> problems;
                T value = null;
                try {
                    value = json.readValue(c.content(), req.type());
                    problems = value == null ? List.of("Output was empty") : req.validator().apply(value);
                } catch (JsonProcessingException e) {
                    problems = List.of("Output is not valid JSON for the schema: " + e.getOriginalMessage());
                }
                if (problems.isEmpty()) {
                    record(req, inputHash, attempt, AiCallLog.Status.VALID, c, null);
                    long latency = Duration.ofNanos(System.nanoTime() - started).toMillis();
                    return new AiResult<>(value, config.model(), req.prompt().version(), attempt, latency);
                }
                record(req, inputHash, attempt, AiCallLog.Status.INVALID, c, String.join("; ", problems));
                log.info("AI {} attempt {} invalid: {}", req.feature(), attempt, problems);
                messages.add(new Message("assistant", c.content()));
                messages.add(new Message("user", "Your previous answer had these problems:\n- "
                        + String.join("\n- ", problems)
                        + "\nReturn the corrected JSON only, following the schema exactly."));
            }
            throw new AiException(Reason.INVALID_OUTPUT,
                    "Model output failed validation " + MAX_ATTEMPTS + " times");
        } finally {
            permits.release();
        }
    }

    private record Completion(String content, Integer promptTokens, Integer completionTokens, int latencyMs) {}

    private <T> Completion call(AiRequest<T> req, List<Message> messages, int attempt, String inputHash) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("messages", messages.stream().map(m -> Map.of("role", m.role(), "content", m.content())).toList());
        body.put("temperature", req.prompt().temperature());
        body.put("stream", false);
        body.put("response_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of("name", req.feature(), "strict", true, "schema", req.schema())));

        HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }

        long started = System.nanoTime();
        CompletableFuture<HttpResponse<String>> future = http.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> response;
        try {
            response = future.get();
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new AiException(Reason.CANCELLED, "Cancelled");
        } catch (ExecutionException e) {
            int latency = elapsedMs(started);
            if (e.getCause() instanceof HttpTimeoutException) {
                record(req, inputHash, attempt, AiCallLog.Status.TIMEOUT, new Completion(null, null, null, latency),
                        "timeout after " + config.timeoutSeconds() + "s");
                throw new AiException(Reason.TIMEOUT, "Model call timed out", e.getCause());
            }
            record(req, inputHash, attempt, AiCallLog.Status.ERROR, new Completion(null, null, null, latency),
                    String.valueOf(e.getCause()));
            throw new AiException(Reason.UNAVAILABLE, "Model endpoint unreachable", e.getCause());
        }

        int latency = elapsedMs(started);
        if (response.statusCode() != 200) {
            String snippet = response.body().substring(0, Math.min(300, response.body().length()));
            record(req, inputHash, attempt, AiCallLog.Status.ERROR, new Completion(null, null, null, latency),
                    "HTTP " + response.statusCode() + ": " + snippet);
            throw new AiException(Reason.UNAVAILABLE, "Model endpoint returned HTTP " + response.statusCode());
        }
        try {
            JsonNode root = json.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            JsonNode usage = root.path("usage");
            return new Completion(clean(content),
                    usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : null,
                    usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : null,
                    latency);
        } catch (JsonProcessingException e) {
            record(req, inputHash, attempt, AiCallLog.Status.ERROR, new Completion(null, null, null, latency),
                    "Unparseable response envelope");
            throw new AiException(Reason.UNAVAILABLE, "Unparseable response from model endpoint", e);
        }
    }

    /** Removes thinking blocks and markdown fences some models add despite instructions. */
    static String clean(String content) {
        String s = THINK.matcher(content).replaceAll("").strip();
        var fence = FENCE.matcher(s);
        return fence.matches() ? fence.group(1) : s;
    }

    private void record(AiRequest<?> req, String inputHash, int attempt, AiCallLog.Status status, Completion c,
            String error) {
        try {
            callLog.save(new AiCallLog(req.feature(), req.prompt().version(), config.model(), inputHash, attempt,
                    status, c.promptTokens(), c.completionTokens(), c.latencyMs(), error));
        } catch (RuntimeException e) {
            log.warn("Could not write ai_call_log", e);
        }
    }

    private static int elapsedMs(long startedNanos) {
        return (int) Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    /** Lightweight reachability probe used by the status endpoint. */
    public boolean ping() {
        if (!config.enabled()) return false;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/models"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .GET()
                    .build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
