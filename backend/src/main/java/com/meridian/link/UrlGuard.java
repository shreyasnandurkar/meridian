package com.meridian.link;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * SSRF guard for server-side link fetching: only http(s) on standard ports, no credentials in the URL, and the host
 * must resolve to public addresses only (no loopback, private, link-local incl. cloud metadata, CGNAT, multicast or
 * reserved ranges). Applied again to every redirect hop.
 */
public class UrlGuard {

    public static final int MAX_URL_LENGTH = 2048;
    private static final Set<Integer> ALLOWED_PORTS = Set.of(-1, 80, 443, 8080, 8443);

    /** Rejected URL; the message is safe to show to the user. */
    public static class BlockedUrlException extends RuntimeException {
        public BlockedUrlException(String message) {
            super(message);
        }
    }

    private final boolean testMode;

    public UrlGuard() {
        this(false);
    }

    /** {@code testMode} allows local addresses and any port, for tests against a local server only. */
    UrlGuard(boolean testMode) {
        this.testMode = testMode;
    }

    /** Parses and checks the shape of a user-supplied URL. Does not resolve the host. */
    public static URI parse(String raw) {
        return parse(raw, false);
    }

    /** {@link #parse(String)} with this guard's settings. */
    public URI check(String raw) {
        return parse(raw, testMode);
    }

    private static URI parse(String raw, boolean anyPort) {
        if (raw == null || raw.isBlank()) throw new BlockedUrlException("Enter a link");
        String s = raw.strip();
        if (s.length() > MAX_URL_LENGTH) throw new BlockedUrlException("That link is too long");
        if (!s.contains("://")) s = "https://" + s;
        URI uri;
        try {
            uri = new URI(s).normalize();
        } catch (URISyntaxException e) {
            throw new BlockedUrlException("That doesn't look like a web link");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new BlockedUrlException("Only http and https links are supported");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) throw new BlockedUrlException("The link has no host");
        if (uri.getRawUserInfo() != null) throw new BlockedUrlException("Links with a username or password are not allowed");
        if (!anyPort && !ALLOWED_PORTS.contains(uri.getPort())) throw new BlockedUrlException("Links on unusual ports are not allowed");
        return uri;
    }

    /** Resolves the host and rejects it unless every address is public. */
    public InetAddress[] resolve(URI uri) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw new BlockedUrlException("That website could not be found");
        }
        if (!testMode) {
            for (InetAddress a : addresses) {
                if (isBlocked(a)) throw new BlockedUrlException("Links to private or local addresses are not allowed");
            }
        }
        return addresses;
    }

    static boolean isBlocked(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) return isBlockedV4(b);
        if (a instanceof Inet6Address) {
            int first = b[0] & 0xff;
            if ((first & 0xfe) == 0xfc) return true; // fc00::/7 unique local
            if (first == 0xfe && (b[1] & 0xc0) == 0xc0) return true; // fec0::/10 old site-local
            boolean leadingZeros = true;
            for (int i = 0; i < 10; i++) leadingZeros &= b[i] == 0;
            // ::a.b.c.d (compatible) and ::ffff:a.b.c.d (mapped) embed an IPv4 address
            if (leadingZeros && ((b[10] == 0 && b[11] == 0) || (b[10] == (byte) 0xff && b[11] == (byte) 0xff))) {
                return isBlockedV4(new byte[] {b[12], b[13], b[14], b[15]});
            }
            // 64:ff9b::/96 NAT64
            if (first == 0x00 && b[1] == 0x64 && b[2] == (byte) 0xff && b[3] == (byte) 0x9b) {
                return isBlockedV4(new byte[] {b[12], b[13], b[14], b[15]});
            }
        }
        return false;
    }

    private static boolean isBlockedV4(byte[] b) {
        int o1 = b[0] & 0xff;
        int o2 = b[1] & 0xff;
        return o1 == 0 // "this" network
                || o1 == 10
                || o1 == 127
                || (o1 == 100 && o2 >= 64 && o2 <= 127) // CGNAT
                || (o1 == 169 && o2 == 254) // link-local, cloud metadata
                || (o1 == 172 && o2 >= 16 && o2 <= 31)
                || (o1 == 192 && o2 == 0 && (b[2] & 0xff) == 0)
                || (o1 == 192 && o2 == 168)
                || (o1 == 198 && (o2 == 18 || o2 == 19)) // benchmarking
                || o1 >= 224; // multicast, reserved, broadcast
    }
}
