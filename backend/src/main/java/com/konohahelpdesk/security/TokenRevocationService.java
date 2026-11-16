package com.konohahelpdesk.security;

import com.konohahelpdesk.model.user.RevokedToken;
import com.konohahelpdesk.repository.user.RevokedTokenRepository;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Makes "Log out" end the session on the server, not only in the browser.
 *
 * The session is a signed JWT, valid until it expires. Clearing the cookie stops this browser
 * sending it, but a copy of the token taken earlier kept working for the rest of its lifetime.
 * Logging out now records the token's hash; JwtCookieFilter refuses any token found here.
 *
 * Kept in the database so it holds across restarts and across several backend containers.
 * The cost is one primary-key lookup per authenticated request.
 */
@Service
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);

    private final RevokedTokenRepository repository;
    private final JwtService jwtService;

    public TokenRevocationService(RevokedTokenRepository repository, JwtService jwtService) {
        this.repository = repository;
        this.jwtService = jwtService;
    }

    /** Signs this token out. Does nothing for a token that is missing, forged or already expired. */
    @Transactional
    public void revoke(String token) {
        Claims claims = jwtService.parseClaims(token);
        if (claims == null || claims.getExpiration() == null) return;
        String hash = AccountTokenService.sha256(token);
        if (repository.existsById(hash)) return;
        LocalDateTime expiresAt = LocalDateTime.ofInstant(claims.getExpiration().toInstant(), ZoneId.systemDefault());
        repository.save(new RevokedToken(hash, expiresAt));
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String token) {
        return token != null && repository.existsById(AccountTokenService.sha256(token));
    }

    /** An expired token is refused on its own, so its row can go. Runs daily at 03:20. */
    @Scheduled(cron = "0 20 3 * * *")
    @Transactional
    public void purgeExpired() {
        int removed = repository.deleteExpiredBefore(LocalDateTime.now());
        if (removed > 0) log.info("Purged {} expired revoked-session rows", removed);
    }
}
