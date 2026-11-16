package com.konohahelpdesk.security;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.store.LoginAttemptStore;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * Per-account failed-login tracking with temporary lockout. Config: app.security.login-lockout.*.
 *
 * The counting lives in a {@link LoginAttemptStore} — in memory by default, Redis when
 * {@code app.store.type=redis} — so the lockout still holds when the backend runs as several
 * containers. The policy (how many failures, how long a lock) stays here.
 */
@Service
public class LoginAttemptService {

    /** Failures older than this are forgotten even without a successful login. */
    private static final Duration FAILURE_MEMORY = Duration.ofHours(1);

    private final AppProperties.LoginLockout cfg;
    private final LoginAttemptStore store;

    public LoginAttemptService(AppProperties props, LoginAttemptStore store) {
        this.cfg = props.getSecurity().getLoginLockout();
        this.store = store;
    }

    public boolean isLocked(String email) {
        if (!cfg.isEnabled()) return false;
        return store.isLocked(key(email));
    }

    /** @return true if this failure caused the account to become locked */
    public boolean recordFailure(String email) {
        if (!cfg.isEnabled()) return false;
        String key = key(email);
        int failures = store.incrementFailures(key, FAILURE_MEMORY);
        if (failures >= cfg.getMaxFailedAttempts()) {
            store.lock(key, Duration.ofMinutes(cfg.getLockMinutes()));
            return true;
        }
        return false;
    }

    public void recordSuccess(String email) {
        store.clear(key(email));
    }

    public int getLockMinutes() {
        return cfg.getLockMinutes();
    }

    private String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
