package com.meridian.link;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads the title of a pasted link. Every hop goes through {@link UrlGuard}; at most 3 redirects, 5 seconds and 512 KB
 * in total, and only HTML is parsed. Any failure just means "no title" so the user can type one.
 */
@Component
public class LinkPreviewer {

    static final int MAX_REDIRECTS = 3;
    static final int MAX_BYTES = 512 * 1024;
    static final Duration BUDGET = Duration.ofSeconds(5);
    private static final Logger log = LoggerFactory.getLogger(LinkPreviewer.class);
    private static final Pattern CHARSET = Pattern.compile("charset=[\"']?([\\w.:-]+)", Pattern.CASE_INSENSITIVE);

    public record LinkPreview(String url, String host, String title) {}

    private final UrlGuard guard;
    private final HttpClient http;

    public LinkPreviewer() {
        this(new UrlGuard());
    }

    LinkPreviewer(UrlGuard guard) {
        this.guard = guard;
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(BUDGET)
                .build();
    }

    /** Throws {@link UrlGuard.BlockedUrlException} for links that may not be fetched at all. */
    public LinkPreview preview(String rawUrl) {
        URI uri = guard.check(rawUrl);
        guard.resolve(uri);
        return new LinkPreview(uri.toString(), uri.getHost(), fetchTitle(uri).orElse(null));
    }

    Optional<String> fetchTitle(URI start) {
        Instant deadline = Instant.now().plus(BUDGET);
        URI uri = start;
        try {
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                if (hop > 0) guard.resolve(uri);
                Duration left = Duration.between(Instant.now(), deadline);
                if (left.isNegative() || left.isZero()) return Optional.empty();
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(left)
                        .header("User-Agent", "Mozilla/5.0 (compatible; Meridian/0.3 link preview)")
                        .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1")
                        .GET()
                        .build();
                HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    response.body().close();
                    String location = response.headers().firstValue("Location").orElse(null);
                    if (location == null) return Optional.empty();
                    uri = guard.check(uri.resolve(location).toString());
                    continue;
                }
                try (InputStream body = response.body()) {
                    if (status != 200) return Optional.empty();
                    String type = response.headers().firstValue("Content-Type").orElse("text/html");
                    if (!type.toLowerCase(Locale.ROOT).contains("html")) return Optional.empty();
                    byte[] bytes = readWithin(body, Duration.between(Instant.now(), deadline));
                    return Optional.ofNullable(TitleExtractor.extract(new String(bytes, charset(type, bytes))));
                }
            }
            return Optional.empty();
        } catch (UrlGuard.BlockedUrlException e) {
            log.info("Link preview redirect blocked: {}", e.getMessage());
            return Optional.empty();
        } catch (IOException | IllegalArgumentException e) {
            log.info("Link preview failed for {}: {}", start.getHost(), e.toString());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** Reads at most {@link #MAX_BYTES}; a server that trickles bytes past the deadline gets cut off. */
    private static byte[] readWithin(InputStream body, Duration left) throws IOException, InterruptedException {
        if (left.isNegative() || left.isZero()) throw new IOException("time budget used up");
        CompletableFuture<byte[]> read = CompletableFuture.supplyAsync(() -> {
            try {
                return body.readNBytes(MAX_BYTES);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, Thread::startVirtualThread);
        try {
            return read.get(left.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            body.close();
            throw new IOException("body read timed out");
        } catch (ExecutionException e) {
            throw new IOException("body read failed", e.getCause());
        }
    }

    /** Charset from the Content-Type header, else from a meta tag near the top, else UTF-8. */
    static Charset charset(String contentType, byte[] body) {
        Matcher m = CHARSET.matcher(contentType);
        if (!m.find()) {
            String head = new String(body, 0, Math.min(body.length, 2048), StandardCharsets.ISO_8859_1);
            m = CHARSET.matcher(head);
            if (!m.find()) return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(m.group(1));
        } catch (IllegalArgumentException e) {
            return StandardCharsets.UTF_8;
        }
    }
}
