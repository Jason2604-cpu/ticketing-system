package com.konohahelpdesk.store;

import java.time.Duration;

/**
 * Fixed-window request counter, keyed by scope + client (e.g. "auth" + IP).
 *
 * Why an interface: the in-memory implementation is correct for ONE backend container. The moment
 * there are two, each keeps its own counts and the effective limit doubles. A Redis implementation
 * ({@code INCR key; EXPIRE key window NX}) shares the counter across every instance — select it with
 * {@code app.store.type=redis}. Nothing that calls this changes.
 */
public interface RateLimitStore {

    /**
     * Counts one hit and reports whether the caller is now over {@code limit} for the current window.
     *
     * @return true when this hit exceeded the limit (the request should be rejected)
     */
    boolean hitAndCheckExceeded(String scope, String key, int limit, Duration window);
}
