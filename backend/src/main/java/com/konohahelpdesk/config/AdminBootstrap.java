package com.konohahelpdesk.config;

import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the built-in default admin on first startup, so a fresh install can be signed into
 * without putting any credentials in .env:
 *
 * <pre>
 *   email:    admin@admin.com
 *   password: admin
 * </pre>
 *
 * The account is flagged {@code mustChangePassword}: until its owner picks a real password
 * (which must pass the normal password policy), the API only answers /auth/me,
 * /auth/change-password and /auth/logout for it — see JwtCookieFilter and SecurityConfig — and
 * the frontend sends it straight to the "set a new password" screen.
 *
 * Runs on every startup but does nothing while any non-deleted ADMIN exists. That also makes it
 * the recovery path: if every admin is ever deleted, a restart brings the default admin back
 * (again with a forced password change).
 */
@Component
@Order(0)
public class AdminBootstrap implements ApplicationRunner {

    public static final String DEFAULT_ADMIN_EMAIL = "admin@admin.com";
    /** Deliberately trivial and well known — it only ever unlocks the change-password screen. */
    public static final String DEFAULT_ADMIN_PASSWORD = "admin";

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogger audit;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditLogger audit) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.countByRoleAndStatusNot(Roles.ADMIN, UserStatus.IS_DELETED) > 0) {
            return; // an admin exists — nothing to do
        }
        if (userRepository.existsByEmailIgnoreCaseAndStatusNot(DEFAULT_ADMIN_EMAIL, UserStatus.IS_DELETED)) {
            // Someone (not an admin) already holds the address; creating a second live row would
            // violate uq_users_email_active. Refuse loudly rather than promote a stranger to admin.
            log.error("No ADMIN account exists, but {} is already used by a non-admin user — the default admin "
                + "was NOT created. Remove or rename that user, then restart.", DEFAULT_ADMIN_EMAIL);
            return;
        }

        User admin = new User();
        admin.setUserCode(nextStaffCode());
        admin.setEmail(DEFAULT_ADMIN_EMAIL);
        admin.setFirstName("System");
        admin.setLastName("Administrator");
        admin.setPhone("0000000000");
        admin.setRole(Roles.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        // The password policy is skipped for this one value on purpose: it is never usable for
        // real work, and the replacement the admin chooses IS checked against the policy.
        admin.setPassword(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
        admin.setMustChangePassword(true);
        userRepository.save(admin);

        audit.event("ADMIN_BOOTSTRAPPED", AuditLogger.describe(admin) + " · default credentials, password change required", admin);
        log.warn("Created the default ADMIN account {} / {} — sign in and set a new password (required before anything else works).",
            DEFAULT_ADMIN_EMAIL, DEFAULT_ADMIN_PASSWORD);
    }

    /** Same EMPxxxxx scheme as UserService, skipping codes still held by soft-deleted users. */
    private String nextStaffCode() {
        long n = 1;
        String code;
        do {
            code = String.format("EMP%05d", n++);
        } while (userRepository.existsByUserCode(code));
        return code;
    }
}
