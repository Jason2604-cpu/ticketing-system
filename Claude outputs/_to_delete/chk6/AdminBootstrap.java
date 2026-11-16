package com.konohahelpdesk.config;

import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
import com.konohahelpdesk.security.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Solves the chicken-and-egg problem of the invitation flow: users are created by
 * admins, so the very first admin has to come from configuration.
 *
 * Runs on every startup but does nothing once any ADMIN account exists.
 */
@Component
@Order(0)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppProperties.Bootstrap cfg;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditLogger audit;

    public AdminBootstrap(AppProperties props, UserRepository userRepository, PasswordEncoder passwordEncoder,
                          PasswordPolicy passwordPolicy, AuditLogger audit) {
        this.cfg = props.getBootstrap();
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.countByRoleAndStatusNot(Roles.ADMIN, UserStatus.IS_DELETED) > 0) {
            return; // an admin exists — bootstrap values are ignored
        }
        if (cfg.getAdminEmail() == null || cfg.getAdminEmail().isBlank() || cfg.getAdminPassword() == null || cfg.getAdminPassword().isBlank()) {
            log.warn("No ADMIN account exists and BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD are not set in .env — nobody can log in.");
            return;
        }
        passwordPolicy.validate(cfg.getAdminPassword()); // fail fast with a clear message

        User admin = new User();
        admin.setUserCode("EMP00001");
        admin.setEmail(cfg.getAdminEmail().trim().toLowerCase(Locale.ROOT));
        admin.setFirstName(cfg.getAdminFirstName());
        admin.setLastName(cfg.getAdminLastName());
        admin.setPhone(cfg.getAdminPhone());
        admin.setRole(Roles.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setPassword(passwordEncoder.encode(cfg.getAdminPassword()));
        userRepository.save(admin);

        audit.event("ADMIN_BOOTSTRAPPED", AuditLogger.describe(admin), admin);
        log.warn("Created the initial ADMIN account '{}' from .env. Change its password after first login.", admin.getEmail());
    }
}
