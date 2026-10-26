package com.meridian.ai;

import java.time.Duration;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tells the UI whether AI is on and the model host is reachable, so it can label fallbacks honestly. */
@RestController
public class AiStatusController {

    public record AiStatus(boolean enabled, boolean reachable, String model) {}

    private static final Duration TTL = Duration.ofSeconds(30);

    private final OpenAiCompatibleAiClient client;
    private volatile AiStatus cached;
    private volatile Instant cachedAt = Instant.EPOCH;

    public AiStatusController(OpenAiCompatibleAiClient client) {
        this.client = client;
    }

    @GetMapping("/api/ai/status")
    public AiStatus status() {
        if (cached == null || Instant.now().isAfter(cachedAt.plus(TTL))) {
            cached = new AiStatus(client.enabled(), client.ping(), client.model());
            cachedAt = Instant.now();
        }
        return cached;
    }
}
