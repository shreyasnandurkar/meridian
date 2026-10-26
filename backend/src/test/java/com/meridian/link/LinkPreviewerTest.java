package com.meridian.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Fetches from a local fake website; the guard is told to allow loopback for this test only. */
class LinkPreviewerTest {

    private HttpServer server;
    private String base;
    private final AtomicInteger hits = new AtomicInteger();
    private final LinkPreviewer previewer = new LinkPreviewer(new UrlGuard(true));

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/page", ex -> send(ex, 200, "text/html; charset=utf-8",
                "<head><meta property=\"og:title\" content=\"Kayak trips on the Mandovi\"></head>"));
        server.createContext("/latin1", ex -> send(ex, 200, "text/html; charset=ISO-8859-1",
                "<title>Café Bodega</title>", StandardCharsets.ISO_8859_1));
        server.createContext("/hop", ex -> redirect(ex, "/page"));
        server.createContext("/loop", ex -> redirect(ex, "/loop"));
        server.createContext("/to-private", ex -> redirect(ex, "http://10.0.0.1/secret"));
        server.createContext("/pdf", ex -> send(ex, 200, "application/pdf", "%PDF-1.4"));
        server.createContext("/missing", ex -> send(ex, 404, "text/html", "<title>Not found</title>"));
        server.createContext("/huge", ex -> send(ex, 200, "text/html",
                "x".repeat(LinkPreviewer.MAX_BYTES) + "<title>Too late</title>"));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void redirect(HttpExchange ex, String location) throws IOException {
        hits.incrementAndGet();
        ex.getResponseHeaders().add("Location", location);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    private void send(HttpExchange ex, int status, String type, String body) throws IOException {
        send(ex, status, type, body, StandardCharsets.UTF_8);
    }

    private void send(HttpExchange ex, int status, String type, String body, Charset charset) throws IOException {
        byte[] bytes = body.getBytes(charset);
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private String title(String path) {
        return previewer.fetchTitle(URI.create(base + path)).orElse(null);
    }

    @Test
    void readsTheTitleAndFollowsRedirects() {
        assertThat(title("/page")).isEqualTo("Kayak trips on the Mandovi");
        assertThat(title("/hop")).isEqualTo("Kayak trips on the Mandovi");
        assertThat(title("/latin1")).isEqualTo("Café Bodega");
    }

    @Test
    void stopsAfterThreeRedirects() {
        assertThat(title("/loop")).isNull();
        assertThat(hits.get()).isEqualTo(LinkPreviewer.MAX_REDIRECTS + 1);
    }

    @Test
    void redirectsArePassedThroughTheGuardAgain() {
        List<String> checked = new CopyOnWriteArrayList<>();
        LinkPreviewer strict = new LinkPreviewer(new UrlGuard() {
            @Override
            public InetAddress[] resolve(URI uri) {
                checked.add(uri.getHost());
                return super.resolve(uri);
            }
        });
        assertThat(strict.fetchTitle(URI.create(base + "/to-private"))).isEmpty();
        assertThat(checked).containsExactly("10.0.0.1");
    }

    @Test
    void ignoresNonHtmlErrorsAndOversizedHeads() {
        assertThat(title("/pdf")).isNull();
        assertThat(title("/missing")).isNull();
        assertThat(title("/huge")).isNull();
    }

    @Test
    void previewRejectsPrivateHostsWithTheRealGuard() {
        assertThatThrownBy(() -> new LinkPreviewer().preview(base + "/page"))
                .isInstanceOf(UrlGuard.BlockedUrlException.class);
        assertThatThrownBy(() -> new LinkPreviewer().preview("http://localhost/page"))
                .isInstanceOf(UrlGuard.BlockedUrlException.class)
                .hasMessageContaining("private or local");
    }
}
