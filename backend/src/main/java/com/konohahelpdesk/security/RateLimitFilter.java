package com.konohahelpdesk.security;

import com.konohahelpdesk.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import com.konohahelpdesk.store.RateLimitStore;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Per-IP fixed-window rate limiter.
 *
 * Two buckets: a general one for every /api/** request and a much stricter one
 * for the anonymous auth endpoints (login, activate, forgot/reset password —
 * brute force and token guessing). Limits come from app.security.rate-limit.*.
 *
 * The counters live in a {@link RateLimitStore}: in memory by default, Redis when
 * app.store.type=redis, so limits hold across several backend containers.
 *
 * Behind the nginx container, set TRUST_FORWARDED_FOR=true so the limit is per real client rather
 * than per nginx. nginx overwrites X-Forwarded-For with $remote_addr, so a client can't spoof it.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final AppProperties.RateLimit cfg;
    /** Routed through the bean rather than a bare slf4j logger so rejections reach the audit viewer. */
    private final AuditLogger auditLogger;
    private final RateLimitStore store;
    private final JwtService jwtService;
    private final String cookieName;

    public RateLimitFilter(AppProperties props, AuditLogger auditLogger, RateLimitStore store, JwtService jwtService) {
        this.jwtService = jwtService;
        this.cookieName = props.getSecurity().getCookie().getName();
        this.cfg = props.getSecurity().getRateLimit();
        this.auditLogger = auditLogger;
        this.store = store;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !cfg.isEnabled() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String ip = clientIp(request);
        String uri = request.getRequestURI();
        boolean isSensitive = "POST".equalsIgnoreCase(request.getMethod())
                && (uri.equals("/api/auth/login") || uri.equals("/api/auth/activate")
                    || uri.equals("/api/auth/forgot-password") || uri.equals("/api/auth/reset-password"));

        if (isSensitive && store.hitAndCheckExceeded("auth", ip, cfg.getLoginAttemptsPerMinute(), WINDOW)) {
            auditLogger.warn("RATE_LIMIT", "scope=auth ip=" + ip + " uri=" + uri);
            reject(response);
            return;
        }
        // The general budget is per signed-in user, and per IP only for anonymous traffic. Keyed by
        // IP alone, everyone in one office (one public IP) shared a single budget, and a handful
        // of open tabs polling every 20 s was enough to get the whole office 429s.
        String client = signedInUser(request);
        if (client == null) client = ip;
        if (store.hitAndCheckExceeded("api", client, cfg.getRequestsPerMinute(), WINDOW)) {
            auditLogger.warn("RATE_LIMIT", "scope=api client=" + client + " ip=" + ip + " uri=" + uri);
            reject(response);
            return;
        }

        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setContentType("application/json");
        response.getWriter().write("{\"timestamp\":\"" + Instant.now()
            + "\",\"status\":429,\"error\":\"Too many requests. Please try again shortly.\"}");
    }

    /**
     * "user:<id>" when the request carries a session token with a valid signature, else null.
     * The id can't be forged — it comes out of the signed token — so nobody can spend another
     * user's budget or mint themselves fresh ones.
     */
    private String signedInUser(HttpServletRequest request) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (jakarta.servlet.http.Cookie c : cookies) {
            if (!cookieName.equals(c.getName())) continue;
            io.jsonwebtoken.Claims claims = jwtService.parseClaims(c.getValue());
            return claims == null || claims.getSubject() == null ? null : "user:" + claims.getSubject();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        if (cfg.isTrustForwardedFor()) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
