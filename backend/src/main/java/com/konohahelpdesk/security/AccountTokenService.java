package com.konohahelpdesk.security;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.model.user.AccountToken;
import com.konohahelpdesk.model.user.AccountTokenType;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.repository.user.AccountTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issues and redeems the one-time tokens used in activation / password-reset emails.
 *
 *  - 256 bits of SecureRandom, base64url encoded → the link.
 *  - Only SHA-256(token) is persisted.
 *  - Issuing a new token invalidates any older unused ones of the same type.
 *  - Tokens are single-use and expire per app.security.tokens.*.
 */
@Service
public class AccountTokenService {

    private static final Logger log = LoggerFactory.getLogger(AccountTokenService.class);
    private static final SecureRandom RNG = new SecureRandom();

    private final AccountTokenRepository repository;
    private final AppProperties.Tokens cfg;

    public AccountTokenService(AccountTokenRepository repository, AppProperties props) {
        this.repository = repository;
        this.cfg = props.getSecurity().getTokens();
    }

    /** @return the raw token to embed in the email link (never stored). */
    @Transactional
    public String issue(User user, AccountTokenType type) {
        repository.invalidateAll(user.getId(), type, LocalDateTime.now());

        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Duration ttl = type == AccountTokenType.ACTIVATION
            ? Duration.ofHours(cfg.getInviteTtlHours())
            : Duration.ofMinutes(cfg.getResetTtlMinutes());

        AccountToken token = new AccountToken();
        token.setUser(user);
        token.setType(type);
        token.setTokenHash(sha256(raw));
        token.setExpiresAt(LocalDateTime.now().plus(ttl));
        repository.save(token);
        return raw;
    }

    /** Validates and burns the token. @throws BusinessRuleException with a safe, generic message. */
    @Transactional
    public User redeem(String rawToken, AccountTokenType type) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 128) {
            throw new BusinessRuleException("This link is invalid or has expired");
        }
        AccountToken token = repository.findByTokenHashAndType(sha256(rawToken.trim()), type)
            .orElseThrow(() -> new BusinessRuleException("This link is invalid or has expired"));
        if (!token.isUsable()) {
            throw new BusinessRuleException("This link is invalid or has expired");
        }
        token.setUsedAt(LocalDateTime.now());
        repository.save(token);
        // A user should never hold two live tokens of the same type after redeeming one.
        repository.invalidateAll(token.getUser().getId(), type, LocalDateTime.now());
        return token.getUser();
    }

    /** Housekeeping: drop tokens that expired more than a day ago. Runs daily at 03:15. */
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpired() {
        int removed = repository.deleteExpiredBefore(LocalDateTime.now().minusDays(1));
        if (removed > 0) log.info("Purged {} expired account tokens", removed);
    }

    static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
