package com.konohahelpdesk.service.auth;

import com.konohahelpdesk.exception.AccountLockedException;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.exception.InvalidCredentialsException;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
import com.konohahelpdesk.security.JwtService;
import com.konohahelpdesk.security.LoginAttemptService;
import com.konohahelpdesk.security.PasswordPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private static final String GENERIC_LOGIN_ERROR = "Invalid email or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttempts;
    private final PasswordPolicy passwordPolicy;
    private final AuditLogger audit;

    /** Hash of a random value, used to keep "unknown email" timing identical to "wrong password". */
    private final String dummyHash;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       LoginAttemptService loginAttempts,
                       PasswordPolicy passwordPolicy,
                       AuditLogger audit) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttempts = loginAttempts;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
        this.dummyHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
    }

    public String login(String rawEmail, String password) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);

        if (loginAttempts.isLocked(email)) {
            // Locked-out addresses may or may not belong to a real account; the email is all we
            // can honestly attribute this to without confirming whether the account exists.
            audit.warnAttempt("LOGIN_LOCKED", "email=" + email, email);
            throw new AccountLockedException(
                "Too many failed attempts. Try again in " + loginAttempts.getLockMinutes() + " minutes.");
        }

        Optional<User> userOpt = userRepository.findByEmailIgnoreCaseAndStatusNot(email, UserStatus.IS_DELETED);

        // Always run a bcrypt comparison so response time doesn't reveal whether the email exists.
        String hashToCheck = userOpt.map(User::getPassword).orElse(dummyHash);
        boolean passwordOk = passwordEncoder.matches(password == null ? "" : password, hashToCheck);

        if (userOpt.isEmpty() || !passwordOk) {
            boolean nowLocked = loginAttempts.recordFailure(email);
            audit.warnAttempt("LOGIN_FAILED", "email=" + email + (nowLocked ? " -> LOCKED" : ""), email);
            throw new InvalidCredentialsException(GENERIC_LOGIN_ERROR);
        }

        User user = userOpt.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            // INVITED (not activated yet) or INACTIVE. Same generic message: don't confirm the account exists.
            audit.warn("LOGIN_REFUSED", "status=" + user.getStatus(), user);
            throw new InvalidCredentialsException(GENERIC_LOGIN_ERROR);
        }

        loginAttempts.recordSuccess(email);
        // The subject has to be passed explicitly: the token is issued on the next line, so there
        // is no security context yet for AuditWriter to read.
        audit.event("LOGIN_SUCCESS", "status=" + user.getStatus(), user);
        return jwtService.generateToken(user);
    }

    @Transactional
    public void changePassword(User principal, String currentPassword, String newPassword) {
        // Re-load: the principal object came from the filter and may be detached.
        User user = userRepository.findByIdAndStatusNot(principal.getId(), UserStatus.IS_DELETED)
            .orElseThrow(() -> new InvalidCredentialsException("Unauthorized"));

        if (!passwordEncoder.matches(currentPassword == null ? "" : currentPassword, user.getPassword())) {
            audit.warn("PASSWORD_CHANGE_FAILED", "reason=current-password-mismatch", user);
            throw new InvalidCredentialsException("Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword == null ? "" : newPassword, user.getPassword())) {
            throw new BusinessRuleException("New password must be different from the current password");
        }
        passwordPolicy.validate(newPassword);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        audit.event("PASSWORD_CHANGED", null, user);
    }
}
