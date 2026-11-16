package com.konohahelpdesk.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * {@link RateLimitStore} shared by every backend container through Redis
 * ({@code app.store.type=redis}, the Docker default).
 *
 * One fixed window per key: the first hit creates the counter with the window as its TTL, later
 * hits only increment it. Both steps run in one Lua script, so a crash can never leave a counter
 * without an expiry (which would block that client forever).
 *
 * Fails OPEN: if Redis is unreachable the request is allowed and a warning is logged (at most
 * once a minute) — a Redis hiccup must not turn into every API call returning an error.
 */
@Component
@ConditionalOnProperty(name = "app.store.type", havingValue = "redis")
public class RedisRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitStore.class);

    private static final RedisScript<Long> HIT = RedisScript.of("""
        local n = redis.call('INCR', KEYS[1])
        if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
        return n
        """, Long.class);

    private final StringRedisTemplate redis;
    private final RedisFailureLog failures = new RedisFailureLog(log);

    public RedisRateLimitStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean hitAndCheckExceeded(String scope, String key, int limit, Duration window) {
        try {
            Long n = redis.execute(HIT, List.of("konoha:rl:" + scope + ":" + key), String.valueOf(window.toMillis()));
            return n != null && n > limit;
        } catch (DataAccessException e) {
            failures.warn("rate limiting", e);
            return false;
        }
    }
}
