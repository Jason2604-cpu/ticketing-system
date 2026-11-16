package com.konohahelpdesk.service.auth;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.exception.ResourceNotFoundException;
import com.konohahelpdesk.mail.MailService;
import com.konohahelpdesk.model.user.AccountTokenType;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AccountTokenService;
import com.konohahelpdesk.security.AuditLogger;
import com.konohahelpdesk.security.LoginAttemptService;
import com.konohahelpdesk.security.PasswordPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Account lifecycle by email: invitation → activation (user sets own password),
 * and forgot → reset. Admins never see or choose a user's password.
 */
@Service
public class AccountService {

    private final UserRepository userRepository;
    private final AccountTokenService tokens;
    private final MailService mail;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttempts;
    private final AuditLogger audit;
    private final AppProperties.Tokens tokenCfg;

    public AccountService(UserRepository userRepository,
                          AccountTokenService tokens,
                          MailService mail,
                          PasswordPolicy passwordPolicy,
                          PasswordEncoder passwordEncoder,
                          LoginAttemptService loginAttempts,
                          AuditLogger audit,
                          AppProperties props) {
        this.userRepository = userRepository;
        this.tokens = tokens;
        this.mail = mail;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.loginAttempts = loginAttempts;
        this.audit = audit;
        this.tokenCfg = props.getSecurity().getTokens();
    }

    // ── Invitation / activation ──────────────────────────────────────────────

    /** Emails an activation link. Called on user creation and by "resend invite". */
    @Transactional
    public void sendActivation(User user) {
        if (user.getStatus() != UserStatus.INVITED) {
            throw new BusinessRuleException("This account has already been activated");
        }
        String raw = tokens.issue(user, AccountTokenType.ACTIVATION);
        String link = mail.activationLink(raw);
        mail.send(user.getEmail(),
            "Activate your " + mail.appName() + " account",
            "Hi " + user.getFirstName() + ",\n\n"
            + "An account has been created for you on " + mail.appName() + ".\n"
            + "Click the link below to confirm your email and choose your password:\n\n"
            + link + "\n\n"
            + "This link is valid for " + tokenCfg.getInviteTtlHours() + " hours and can only be used once.\n"
            + "If you weren't expecting this email, you can ignore it.\n");
        audit.event("ACTIVATION_SENT", AuditLogger.describe(user) + " · status=" + user.getStatus(), user);
    }

    @Transactional
    public void resendActivation(Long userId) {
        User user = userRepository.findByIdAndStatusNot(userId, UserStatus.IS_DELETED)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        sendActivation(user);
    }

    /** Redeems the activation token and sets the user's chosen password. */
    @Transactional
    public void activate(String rawToken, String newPassword) {
        User user = tokens.redeem(rawToken, AccountTokenType.ACTIVATION);
        if (user.getStatus() != UserStatus.INVITED) {
            throw new BusinessRuleException("This account has already been activated");
        }
        passwordPolicy.validate(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        // Activation happens before the user can sign in, so there is no security context.
        audit.event("ACCOUNT_ACTIVATED", AuditLogger.describe(user), user);
    }

    // ── Forgot / reset password ──────────────────────────────────────────────

    /**
     * Always succeeds from the caller's point of view (no account enumeration).
     * Only ACTIVE accounts get an email; INVITED accounts get their activation link re-sent.
     */
    @Transactional
    public void requestPasswordReset(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        Optional<User> userOpt = userRepository.findByEmailIgnoreCaseAndStatusNot(email, UserStatus.IS_DELETED);
        if (userOpt.isEmpty()) {
            audit.warnAttempt("PASSWORD_RESET_REQUESTED_UNKNOWN", "email=" + email, email);
            return;
        }
        User user = userOpt.get();
        switch (user.getStatus()) {
            case ACTIVE -> {
                String raw = tokens.issue(user, AccountTokenType.PASSWORD_RESET);
                mail.send(user.getEmail(),
                    "Reset your " + mail.appName() + " password",
                    "Hi " + user.getFirstName() + ",\n\n"
                    + "We received a request to reset your password. Use the link below to choose a new one:\n\n"
                    + mail.resetLink(raw) + "\n\n"
                    + "This link is valid for " + tokenCfg.getResetTtlMinutes() + " minutes and can only be used once.\n"
                    + "If you didn't request this, you can safely ignore this email — your password will not change.\n");
                audit.event("PASSWORD_RESET_SENT", AuditLogger.describe(user), user);
            }
            case INVITED -> sendActivation(user);
            default -> audit.warn("PASSWORD_RESET_REQUESTED_INACTIVE", AuditLogger.describe(user) + " · status=" + user.getStatus(), user);
        }
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        User user = tokens.redeem(rawToken, AccountTokenType.PASSWORD_RESET);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessRuleException("This link is invalid or has expired");
        }
        passwordPolicy.validate(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(java.time.LocalDateTime.now()); // signs out every existing session
        userRepository.save(user);
        loginAttempts.recordSuccess(user.getEmail()); // clear any lockout
        audit.event("PASSWORD_RESET_COMPLETED", AuditLogger.describe(user), user);
    }
}
