package com.konohahelpdesk.security;

import com.konohahelpdesk.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory, per-IP fixed-window rate limiter.
 *
 * Two buckets: a general one for every /api/** request and a much stricter one
 * for the anonymous auth endpoints (login, activate, forgot/reset password —
 * brute force and token guessing). Limits come from app.security.rate-limit.*.
 *
 * Single-instance only: if you scale to multiple app instances, swap the map
 * for Redis (Bucket4j or similar) — the interface stays the same.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000L;
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final AppProperties.RateLimit cfg;
    /** Routed through the bean rather than a bare slf4j logger so rejections reach the audit viewer. */
    private final AuditLogger auditLogger;
    private final Map<String, Window> general = new ConcurrentHashMap<>();
    private final Map<String, Window> login = new ConcurrentHashMap<>();

    public RateLimitFilter(AppProperties props, AuditLogger auditLogger) {
        this.cfg = props.getSecurity().getRateLimit();
        this.auditLogger = auditLogger;
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

        if (isSensitive && exceeded(login, ip, cfg.getLoginAttemptsPerMinute())) {
            auditLogger.warn("RATE_LIMIT", "scope=auth ip=" + ip + " uri=" + uri);
            reject(response);
            return;
        }
        if (exceeded(general, ip, cfg.getRequestsPerMinute())) {
            auditLogger.warn("RATE_LIMIT", "scope=api ip=" + ip + " uri=" + uri);
            reject(response);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean exceeded(Map<String, Window> buckets, String key, int limit) {
        long now = System.currentTimeMillis();
        if (buckets.size() > CLEANUP_THRESHOLD) {
            buckets.entrySet().removeIf(e -> e.getValue().start + WINDOW_MS < now);
        }
        Window w = buckets.compute(key, (k, existing) ->
            (existing == null || existing.start + WINDOW_MS < now) ? new Window(now) : existing);
        return w.count.incrementAndGet() > limit;
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setContentType("application/json");
        response.getWriter().write("{\"timestamp\":\"" + Instant.now()
            + "\",\"status\":429,\"error\":\"Too many requests. Please try again shortly.\"}");
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

    private static final class Window {
        final long start;
        final AtomicInteger count = new AtomicInteger();
        Window(long start) { this.start = start; }
    }
}
