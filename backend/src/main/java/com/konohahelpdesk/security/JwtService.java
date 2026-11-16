package com.konohahelpdesk.security;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.model.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String DEV_DEFAULT_SECRET = "dev-only-insecure-secret-change-me-please-32-bytes-min";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(AppProperties props, Environment env) {
        String secret = props.getSecurity().getJwt().getSecret();
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);

        // Fail fast on a weak or default secret — never let it reach production quietly.
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "app.security.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes (set JWT_SECRET in .env)");
        }
        boolean isProd = Arrays.asList(env.getActiveProfiles()).contains("prod");
        if (DEV_DEFAULT_SECRET.equals(secret)) {
            if (isProd) {
                throw new IllegalStateException("Refusing to start with the dev-only JWT secret in the prod profile. Set JWT_SECRET.");
            }
            log.warn("Using the DEV-ONLY default JWT secret. Set JWT_SECRET in .env before deploying.");
        }

        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMs = props.getSecurity().getJwt().getExpirationMs();
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
            .subject(String.valueOf(user.getId()))
            .claim("role", user.getRole().name())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact();
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }

    /** Returns parsed claims, or null if the token is missing, expired, or invalid. */
    public Claims parseClaims(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
