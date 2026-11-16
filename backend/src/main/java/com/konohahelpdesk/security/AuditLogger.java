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
        event(action, details, null);
    }

    /**
     * Same, but naming the user the event is about when there is no security context to read it
     * from. THE AUTH FLOWS NEED THIS: at the moment LOGIN_SUCCESS is recorded no token has been
     * issued yet, and activation and password reset are by definition performed by someone who is
     * not signed in. Without a subject every one of those rows was attributed to "System".
     *
     * Ignored when a security context exists — you cannot use this to write a row under someone
     * else's name.
     */
    public void event(String action, String details, User subject) {
        audit.info("{} actor={} {}", action, actor(subject), details == null ? "" : details);
        writer.record(action, details, subject, null);
    }

    /**
     * Something was refused or failed. Persisted in its OWN transaction, because the caller is
     * usually about to roll back and the failure is the thing worth keeping.
     */
    public void warn(String action, String details) {
        warn(action, details, null);
    }

    /** As {@link #warn(String, String)}, naming the user the failure concerns. */
    public void warn(String action, String details, User subject) {
        audit.warn("{} actor={} {}", action, actor(subject), details == null ? "" : details);
        writer.recordFailure(action, details, subject, null);
    }

    /**
     * A failure against an identity that may not exist — a sign-in attempt on an unknown address.
     * The email is recorded without a user id, so the log shows what was tried without implying
     * an account behind it.
     */
    public void warnAttempt(String action, String details, String attemptedEmail) {
        audit.warn("{} actor={} {}", action, attemptedEmail == null ? "anonymous" : attemptedEmail,
            details == null ? "" : details);
        writer.recordFailure(action, details, null, attemptedEmail);
    }

    /**
     * Formats a user for an audit detail string: {@code "Jane Doe <jane@acme.com>"}.
     *
     * Details name people rather than carrying a bare {@code userId=7}, because an audit log is
     * read by humans long after the fact and often about accounts that no longer exist — the same
     * reason the actor columns are a snapshot rather than a foreign key. An id alone means a
     * lookup that may no longer resolve.
     *
     * Only ever used for the SUBJECT of an event (the user being created, deactivated, re-roled).
     * The person who DID it is the row's actor and must not be repeated here.
     */
    public static String describe(User u) {
        if (u == null) return "unknown user";
        String name = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                     + (u.getLastName() == null ? "" : u.getLastName())).trim();
        String email = u.getEmail();
        if (name.isEmpty()) return email == null ? "user#" + u.getId() : email;
        return email == null ? name : name + " <" + email + ">";
    }

    private String actor(User subject) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User u = auth != null && auth.getPrincipal() instanceof User signedIn ? signedIn : subject;
        return u == null ? "anonymous" : u.getId() + "(" + (u.getRole() == null ? "?" : u.getRole().name()) + ")";
    }
}
