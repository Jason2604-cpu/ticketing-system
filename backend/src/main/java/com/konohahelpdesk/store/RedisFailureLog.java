package com.konohahelpdesk.store;

import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicLong;

/** Logs "Redis unreachable" at most once a minute per store, instead of once per request. */
final class RedisFailureLog {

    private static final long INTERVAL_MS = 60_000;

    private final Logger log;
    private final AtomicLong lastWarn = new AtomicLong();

    RedisFailureLog(Logger log) {
        this.log = log;
    }

    void warn(String what, Exception e) {
        long now = System.currentTimeMillis();
        long last = lastWarn.get();
        if (now - last >= INTERVAL_MS && lastWarn.compareAndSet(last, now)) {
            log.warn("Redis unavailable — {} is running without shared counters (fail-open): {}", what, e.getMessage());
        }
    }
}
