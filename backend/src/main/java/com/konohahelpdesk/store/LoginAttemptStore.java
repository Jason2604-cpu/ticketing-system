package com.konohahelpdesk.store;

import java.time.Duration;

/**
 * Per-account failed-login counts and temporary locks.
 *
 * Same reasoning as {@link RateLimitStore}: in memory is fine for one backend container; with
 * several, an attacker spreads guesses across instances and each one only sees a fraction. The
 * Redis shape is {@code INCR fail:<email>} with a TTL, and {@code SET lock:<email> 1 EX <seconds>}.
 * Keys passed in are already normalised (trimmed, lower-cased).
 */
public interface LoginAttemptStore {

    boolean isLocked(String key);

    /** Adds one failure and returns the new count. The count expires after {@code ttl} of quiet. */
    int incrementFailures(String key, Duration ttl);

    /** Locks the account for {@code duration} and resets its failure count. */
    void lock(String key, Duration duration);

    /** Clears failures and any lock — called after a successful login. */
    void clear(String key);
}
