package com.konohahelpdesk.security;

import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.service.audit.AuditWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Structured security/audit events on the dedicated "AUDIT" logger, and — since the audit log
 * viewer — a row in {@code audit_events} as well.
 *
 * Both sinks on purpose. The log line is the one that survives the database being the thing
 * that's broken, and it's what a SIEM tails; the row is what the admin viewer queries. The
 * signature is unchanged, so all ~55 existing call sites persist without being touched.
 *
 * Never log credentials, tokens, or full request bodies here.
 */
@Component
public class AuditLogger {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    private final AuditWriter writer;

    public AuditLogger(AuditWriter writer) {
        this.writer = writer;
    }

    /** Something happened and it was allowed. Persisted in the caller's transaction. */
    public void event(String action, String details) {
        audit.info("{} actor={} {}", action, actor(), details == null ? "" : details);
        writer.record(action, details);
    }

    /**
     * Something was refused or failed. Persisted in its OWN transaction, because the caller is
     * usually about to roll back and the failure is the thing worth keeping.
     */
    public void warn(String action, String details) {
        audit.warn("{} actor={} {}", action, actor(), details == null ? "" : details);
        writer.recordFailure(action, details);
    }

    private String actor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User u) {
            return u.getId() + "(" + u.getRole().name() + ")";
        }
        return "anonymous";
    }
}
