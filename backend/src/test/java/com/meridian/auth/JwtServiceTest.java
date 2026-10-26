package com.meridian.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meridian.config.MeridianProperties;
import com.meridian.config.SecurityConfig;
import com.meridian.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-definitely-longer-than-32-bytes";

    private final MeridianProperties props = new MeridianProperties(
            "http://localhost:3000", new MeridianProperties.Jwt(SECRET, Duration.ofHours(24)), null, null);
    private final SecurityConfig config = new SecurityConfig(props);
    private final SecretKey key = config.jwtSigningKey();
    private final JwtDecoder decoder = config.jwtDecoder(key);

    @Test
    void issuedTokenRoundTripsWithSubjectAndExpiry() {
        User user = new User("a@example.com", "hash", "Asha");
        String token = new JwtService(config.jwtEncoder(key), props).issue(user);

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("a@example.com");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void expiredTokenIsRejected() {
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofDays(2)), ZoneOffset.UTC);
        String token = new JwtService(config.jwtEncoder(key), props, past)
                .issue(new User("b@example.com", "hash", "Ben"));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithOtherSecretIsRejected() {
        MeridianProperties other = new MeridianProperties(
                null, new MeridianProperties.Jwt("another-secret-that-is-also-longer-than-32-bytes", Duration.ofHours(1)),
                null, null);
        SecurityConfig otherConfig = new SecurityConfig(other);
        String token = new JwtService(otherConfig.jwtEncoder(otherConfig.jwtSigningKey()), other)
                .issue(new User("c@example.com", "hash", "Chen"));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretFailsFast() {
        SecurityConfig weak = new SecurityConfig(
                new MeridianProperties(null, new MeridianProperties.Jwt("short", Duration.ofHours(1)), null, null));

        assertThatThrownBy(weak::jwtSigningKey).isInstanceOf(IllegalStateException.class);
    }
}
