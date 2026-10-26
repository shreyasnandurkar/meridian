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

    /**
     * @param radiusKm default search radius around the trip destination
     * @param cacheTtl how long a provider answer is reused before it is fetched again
     */
    public record Places(String provider, String overpassUrl, String nominatimUrl, int radiusKm, Duration cacheTtl) {}
}
