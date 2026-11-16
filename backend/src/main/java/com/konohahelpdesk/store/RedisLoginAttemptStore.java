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
 * {@link LoginAttemptStore} in Redis ({@code app.store.type=redis}, the Docker default):
 * failed-login counts and temporary locks survive a backend restart and are shared by every
 * backend container.
 *
 * <pre>
 *   konoha:fail:&lt;email&gt;  counter, TTL = failure memory (reset on every new failure)
 *   konoha:lock:&lt;email&gt;  "1",     TTL = lock duration
 * </pre>
 *
 * Fails OPEN like {@link RedisRateLimitStore}: with Redis down, logins still work (without the
 * lockout) rather than nobody being able to sign in.
 */
@Component
@ConditionalOnProperty(name = "app.store.type", havingValue = "redis")
public class RedisLoginAttemptStore implements LoginAttemptStore {

    private static final Logger log = LoggerFactory.getLogger(RedisLoginAttemptStore.class);

    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
        local n = redis.call('INCR', KEYS[1])
        redis.call('PEXPIRE', KEYS[1], ARGV[1])
        return n
        """, Long.class);

    private final StringRedisTemplate redis;
    private final RedisFailureLog failures = new RedisFailureLog(log);

    public RedisLoginAttemptStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean isLocked(String key) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(lockKey(key)));
        } catch (DataAccessException e) {
            failures.warn("login lockout", e);
            return false;
        }
    }

    @Override
    public int incrementFailures(String key, Duration ttl) {
        try {
            Long n = redis.execute(INCREMENT, List.of(failKey(key)), String.valueOf(ttl.toMillis()));
            return n == null ? 0 : n.intValue();
        } catch (DataAccessException e) {
            failures.warn("login lockout", e);
            return 0;
        }
    }

    @Override
    public void lock(String key, Duration duration) {
        try {
            redis.opsForValue().set(lockKey(key), "1", duration);
            redis.delete(failKey(key));
        } catch (DataAccessException e) {
            failures.warn("login lockout", e);
        }
    }

    @Override
    public void clear(String key) {
        try {
            redis.delete(List.of(failKey(key), lockKey(key)));
        } catch (DataAccessException e) {
            failures.warn("login lockout", e);
        }
    }

    private static String failKey(String key) { return "konoha:fail:" + key; }
    private static String lockKey(String key) { return "konoha:lock:" + key; }
}
