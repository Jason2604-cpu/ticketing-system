package com.konohahelpdesk.dto.audit;

import com.konohahelpdesk.model.audit.AuditEvent;
import com.konohahelpdesk.model.audit.AuditLevel;

import java.time.LocalDateTime;

/** One row in the audit viewer. */
public class AuditEventResponse {

    private Long id;
    private String action;
    private AuditLevel level;
    private Long actorId;
    private String actorName;
    private String actorEmail;
    private String actorRole;
    private String details;
    private String ipAddress;
    private LocalDateTime createdAt;

    public static AuditEventResponse from(AuditEvent e) {
        AuditEventResponse r = new AuditEventResponse();
        r.id = e.getId();
        r.action = e.getAction();
        r.level = e.getLevel();
        r.actorId = e.getActorId();
        r.actorName = e.getActorName();
        r.actorEmail = e.getActorEmail();
        r.actorRole = e.getActorRole();
        r.details = e.getDetails();
        r.ipAddress = e.getIpAddress();
        r.createdAt = e.getCreatedAt();
        return r;
    }

    public Long getId() { return id; }
    public String getAction() { return action; }
    public AuditLevel getLevel() { return level; }
    public Long getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public String getActorEmail() { return actorEmail; }
    public String getActorRole() { return actorRole; }
    public String getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
