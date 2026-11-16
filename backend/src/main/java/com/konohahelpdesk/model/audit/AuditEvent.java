package com.konohahelpdesk.model.audit;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One recorded audit event. Written by {@code AuditLogger} alongside the existing slf4j line, so
 * every one of the ~55 {@code audit.event(...)} call sites already in the app starts producing
 * rows without being touched.
 *
 * THE ACTOR IS A SNAPSHOT, NOT A FOREIGN KEY. There is an {@code actorId} for filtering, but the
 * name, email and role are copied in at write time and never joined back to {@code users}. Two
 * reasons: an audit trail has to survive the user being deleted (which is exactly when you most
 * want to read it), and a later rename must not silently rewrite who did what last year.
 */
@Entity
@Table(name = "audit_events", indexes = {
    @Index(name = "idx_audit_created", columnList = "created_at"),
    @Index(name = "ix_audit_action_created", columnList = "action, created_at"),
    @Index(name = "ix_audit_actor_created", columnList = "actor_id, created_at")
})
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The event name, e.g. USER_CREATED, LOGIN_FAILED. Same vocabulary as the log lines. */
    @Column(nullable = false, length = 60)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuditLevel level = AuditLevel.INFO;

    /** Null for anonymous events — a failed login against an address that doesn't exist. */
    @Column(name = "actor_id")
    private Long actorId;

    /** Snapshot of the actor's display name at the time. "System" for unauthenticated events. */
    @Column(name = "actor_name", length = 120)
    private String actorName;

    @Column(name = "actor_email", length = 190)
    private String actorEmail;

    /** Snapshot of the actor's primary role name. Null for anonymous. */
    @Column(name = "actor_role", length = 30)
    private String actorRole;

    /** The free-form detail string the call site passed, e.g. "userId=7 role=ENGINEER". */
    @Column(columnDefinition = "text")
    private String details;

    /** Caller IP where one is available. Only ever the request's own — never a spoofable header. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public AuditLevel getLevel() { return level; }
    public void setLevel(AuditLevel level) { this.level = level; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public String getActorEmail() { return actorEmail; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
