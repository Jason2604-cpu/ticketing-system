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
    public void record(String action, String details) {
        save(action, details, AuditLevel.INFO);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String action, String details) {
        save(action, details, AuditLevel.WARN);
    }

    private void save(String action, String details, AuditLevel level) {
        try {
            AuditEvent e = new AuditEvent();
            e.setAction(action);
            e.setLevel(level);
            e.setDetails(details);
            e.setIpAddress(currentIp());

            User actor = currentActor();
            if (actor != null) {
                e.setActorId(actor.getId());
                e.setActorName(actor.getFirstName() + " " + actor.getLastName());
                e.setActorEmail(actor.getEmail());
                e.setActorRole(actor.getRole().name());
            } else {
                // Startup seeders, scheduled sweeps and pre-auth failures all land here.
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
     */
    private String currentIp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest().getRemoteAddr();
        }
        return null;
    }
}
