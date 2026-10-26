package com.meridian.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meridian")
public record MeridianProperties(String appBaseUrl, Jwt jwt, Ai ai, Places places) {

    public record Jwt(String secret, Duration ttl) {}

    public record Ai(
            boolean enabled,
            String baseUrl,
            String apiKey,
            String model,
            int maxConcurrency,
            int timeoutSeconds) {}

    public record Places(String provider) {}
}
