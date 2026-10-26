package com.meridian.geo;

import org.springframework.stereotype.Component;

/**
 * One request per second to Nominatim across the whole app (destination autocomplete and place search share it), as
 * the public usage policy requires.
 */
@Component
public class OsmRateLimiter {

    /** Identifying User-Agent the OSM services ask for. */
    public static final String USER_AGENT = "Meridian/0.3 (collaborative trip planner; classroom project)";

    static final long MIN_INTERVAL_MS = 1100;

    private final Object lock = new Object();
    private long lastCallAt;

    public void acquire() throws InterruptedException {
        synchronized (lock) {
            long wait = lastCallAt + MIN_INTERVAL_MS - System.currentTimeMillis();
            if (wait > 0) Thread.sleep(wait);
            lastCallAt = System.currentTimeMillis();
        }
    }
}
