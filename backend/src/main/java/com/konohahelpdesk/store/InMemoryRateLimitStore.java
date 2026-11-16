package com.konohahelpdesk.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** The default {@link RateLimitStore}: per-process maps. Correct for a single backend instance. */
@Component
@ConditionalOnProperty(name = "app.store.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryRateLimitStore implements RateLimitStore {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public boolean hitAndCheckExceeded(String scope, String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        long windowMs = window.toMillis();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(e -> e.getValue().start + windowMs < now);
        }
        Window w = windows.compute(scope + ':' + key, (k, existing) ->
            (existing == null || existing.start + windowMs < now) ? new Window(now) : existing);
        return w.count.incrementAndGet() > limit;
    }

    private static final class Window {
        final long start;
        final AtomicInteger count = new AtomicInteger();
        Window(long start) { this.start = start; }
    }
}
