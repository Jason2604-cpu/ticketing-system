package com.konohahelpdesk.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The default {@link LoginAttemptStore}: a per-process map. Correct for a single backend instance. */
@Component
@ConditionalOnProperty(name = "app.store.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryLoginAttemptStore implements LoginAttemptStore {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public boolean isLocked(String key) {
        Entry e = entries.get(key);
        if (e == null) return false;
        Instant now = Instant.now();
        if (e.lockedUntil != null && e.lockedUntil.isAfter(now)) return true;
        // Lock (or the failure count) has lapsed — forget it, like a Redis key expiring.
        if (e.lockedUntil != null || (e.failuresExpireAt != null && e.failuresExpireAt.isBefore(now))) {
            entries.remove(key, e);
        }
        return false;
    }

    @Override
    public int incrementFailures(String key, Duration ttl) {
        Instant now = Instant.now();
        Entry e = entries.compute(key, (k, existing) ->
            existing == null || (existing.failuresExpireAt != null && existing.failuresExpireAt.isBefore(now))
                ? new Entry() : existing);
        synchronized (e) {
            e.failures++;
            e.failuresExpireAt = now.plus(ttl);
            return e.failures;
        }
    }

    @Override
    public void lock(String key, Duration duration) {
        Entry e = new Entry();
        e.lockedUntil = Instant.now().plus(duration);
        entries.put(key, e);
    }

    @Override
    public void clear(String key) {
        entries.remove(key);
    }

    private static final class Entry {
        int failures;
        Instant failuresExpireAt;
        Instant lockedUntil;
    }
}
