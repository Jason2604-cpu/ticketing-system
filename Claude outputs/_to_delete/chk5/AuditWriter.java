package com.konohahelpdesk.service.audit;

import com.konohahelpdesk.model.audit.AuditEvent;
import com.konohahelpdesk.model.audit.AuditLevel;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.repository.audit.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Persists audit events. Split out of {@code AuditLogger} so the {@code @Transactional} boundaries
 * below are real proxies rather than self-invocations, and so the logger itself stays a trivial
 * component that anything can hold.
 *
 * The two propagation modes are the whole design:
 *
 *  - {@link #record} joins the caller's transaction. An audit row saying "ticket created" must not
 *    outlive a rollback that means no ticket was created.
 *  - {@link #recordFailure} runs in its own ({@code REQUIRES_NEW}). Failure events almost always
 *    accompany a rollback — a wrong current password, a refused login, a quota rejection — and
 *    those are precisely the rows you must keep. Joining the caller there would throw away every
 *    record of the thing going wrong.
 *
 * Nothing here throws into the caller: an audit write failing must never turn a successful
 * business operation into an error. The slf4j line is written first for that reason — if the
 * database is the thing that's broken, the log still has the event.
 */
@Service
public class AuditWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditWriter.class);

    private final AuditEventRepository repository;

    public AuditWriter(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String action, String details, User subject, String attemptedEmail) {
        save(action, details, AuditLevel.INFO, subject, attemptedEmail);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String action, String details, User subject, String attemptedEmail) {
        save(action, details, AuditLevel.WARN, subject, attemptedEmail);
    }

    /**
     * Who the row is attributed to, in priority order:
     *
     *  1. The authenticated user, when there is one. Normal case.
     *  2. An explicitly supplied {@code subject}. This exists because THE AUTH FLOWS HAVE NO
     *     SECURITY CONTEXT: at the moment LOGIN_SUCCESS is recorded the token has not been issued
     *     yet, and JwtCookieFilter only populates the context on the NEXT request. Activation and
     *     password reset are the same — the caller is not signed in, by definition. Every one of
     *     those rows used to read "System", which is both wrong and useless in a security log.
     *  3. An {@code attemptedEmail} with no user behind it — a sign-in against an address that
     *     doesn't exist. Recorded as the email with no id, so the viewer can show what was tried
     *     without implying an account.
     *  4. Nothing: genuinely system-initiated work — startup seeders, scheduled sweeps.
     */
    private void save(String action, String details, AuditLevel level, User subject, String attemptedEmail) {
        try {
            AuditEvent e = new AuditEvent();
            e.setAction(action);
            e.setLevel(level);
            e.setDetails(details);
            e.setIpAddress(currentIp());

            User actor = currentActor();
            if (actor == null) actor = subject;

            if (actor != null) {
                e.setActorId(actor.getId());
                e.setActorName(actor.getFirstName() + " " + actor.getLastName());
                e.setActorEmail(actor.getEmail());
                e.setActorRole(actor.getRole() == null ? null : actor.getRole().name());
            } else if (attemptedEmail != null && !attemptedEmail.isBlank()) {
                e.setActorEmail(attemptedEmail.trim());
            } else {
                e.setActorName("System");
            }
            repository.save(e);
        } catch (RuntimeException ex) {
            log.error("Could not persist audit event {}: {}", action, ex.getMessage());
        }
    }

    private User currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof User u ? u : null;
    }

    /**
     * The socket's own address, never X-Forwarded-For — that header is caller-controlled, and
     * app.security.rate-limit.trust-forwarded-for is false for the same reason. Null outside a
     * request (a scheduled sweep, an ApplicationRunner).
     *
     * IPv6 loopback is normalised to 127.0.0.1. A local request arrives as 0:0:0:0:0:0:0:1 or ::1
     * depending on how the client resolved localhost, so the same developer hitting the same
     * machine produced two different-looking addresses in the log for no reason.
     */
    private String currentIp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) return null;
        String ip = sra.getRequest().getRemoteAddr();
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) return "127.0.0.1";
        return ip;
    }
}
