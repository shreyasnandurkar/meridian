package com.meridian.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlGuardTest {

    @Test
    void acceptsOrdinaryWebLinksAndAddsAMissingScheme() {
        assertThat(UrlGuard.parse("https://www.example.com/stay?id=3").getHost()).isEqualTo("www.example.com");
        URI bare = UrlGuard.parse("  example.com/goa  ");
        assertThat(bare.getScheme()).isEqualTo("https");
        assertThat(bare.getPath()).isEqualTo("/goa");
        assertThat(UrlGuard.parse("http://example.com:8080/").getPort()).isEqualTo(8080);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com/a", "file:///etc/passwd", "javascript:alert(1)",
            "https://user:pw@example.com/", "https://example.com:22/", "http://", "https://exa mple.com", ""})
    void rejectsUnsafeOrMalformedLinks(String url) {
        assertThatThrownBy(() -> UrlGuard.parse(url)).isInstanceOf(UrlGuard.BlockedUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.1", "169.254.169.254",
            "100.64.0.1", "0.0.0.0", "224.0.0.1", "255.255.255.255", "198.18.0.1", "::1", "fd00::1", "fe80::1",
            "::ffff:127.0.0.1", "::ffff:10.0.0.1", "64:ff9b::a9fe:a9fe"})
    void blocksPrivateAndReservedAddresses(String ip) throws Exception {
        assertThat(UrlGuard.isBlocked(InetAddress.getByName(ip))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"93.184.216.34", "8.8.8.8", "172.32.0.1", "100.128.0.1", "2606:4700::1111"})
    void allowsPublicAddresses(String ip) throws Exception {
        assertThat(UrlGuard.isBlocked(InetAddress.getByName(ip))).isFalse();
    }

    @Test
    void resolvingALocalHostIsRejected() {
        assertThatThrownBy(() -> new UrlGuard().resolve(URI.create("http://localhost/")))
                .isInstanceOf(UrlGuard.BlockedUrlException.class)
                .hasMessageContaining("private or local");
        assertThatThrownBy(() -> new UrlGuard().resolve(URI.create("http://127.0.0.1:8080/admin")))
                .isInstanceOf(UrlGuard.BlockedUrlException.class);
    }
}
