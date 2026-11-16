package com.konohahelpdesk.security;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.store.InMemoryLoginAttemptStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptServiceTest {

    private LoginAttemptService service(int maxAttempts) {
        AppProperties props = new AppProperties();
        props.getSecurity().getLoginLockout().setMaxFailedAttempts(maxAttempts);
        props.getSecurity().getLoginLockout().setLockMinutes(15);
        return new LoginAttemptService(props, new InMemoryLoginAttemptStore());
    }

    @Test
    void locksAfterConfiguredFailures() {
        LoginAttemptService s = service(3);
        assertFalse(s.recordFailure("A@x.com"));
        assertFalse(s.recordFailure("a@x.com"));       // case-insensitive key
        assertTrue(s.recordFailure("a@x.com "));       // third failure locks
        assertTrue(s.isLocked("a@x.com"));
    }

    @Test
    void successResetsCounter() {
        LoginAttemptService s = service(3);
        s.recordFailure("b@x.com");
        s.recordFailure("b@x.com");
        s.recordSuccess("b@x.com");
        assertFalse(s.recordFailure("b@x.com"));
        assertFalse(s.isLocked("b@x.com"));
    }

    @Test
    void disabledLockoutNeverLocks() {
        AppProperties props = new AppProperties();
        props.getSecurity().getLoginLockout().setEnabled(false);
        props.getSecurity().getLoginLockout().setMaxFailedAttempts(1);
        LoginAttemptService s = new LoginAttemptService(props, new InMemoryLoginAttemptStore());
        assertFalse(s.recordFailure("c@x.com"));
        assertFalse(s.isLocked("c@x.com"));
    }
}
