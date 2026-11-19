package com.konohahelpdesk.model.ticket;

import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
// Schema is owned by Flyway (db/migration). These @Index entries mirror it for readability;
// the partial / expression indexes (open-by-company, finished-at) exist only in V2.
@Table(name = "tickets", indexes = {
        @Index(name = "ix_tickets_company_created", columnList = "company_id, created_at"),
        @Index(name = "ix_tickets_status", columnList = "status"),
        @Index(name = "ix_tickets_assignee", columnList = "assignee_id"),
        @Index(name = "ix_tickets_created", columnList = "created_at")
})
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "steps_to_reproduce", columnDefinition = "TEXT")
    private String stepsToReproduce;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketCategory category = TicketCategory.TECHNICAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketPriority priority = TicketPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status = TicketStatus.OPEN;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** Who typed the ticket in. Set once; never changes (it is the audit record). */
    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /**
     * Who the ticket is FOR — the customer its notifications and emails go to. A customer's own
     * ticket: themselves. Staff pick it when raising a ticket on a company's behalf and can change
     * it later. Null = a company-level ticket, whose customer notices go to the Customer Admins.
     */
    @ManyToOne
    @JoinColumn(name = "requester_id")
    private User requester;

    @ManyToOne
    @JoinColumn(name = "assignee_id")
    private User assignee;

    // SLA targets computed at creation from the company's subscription
    @Column(name = "response_due_at") private LocalDateTime responseDueAt;
    @Column(name = "resolution_due_at") private LocalDateTime resolutionDueAt;
    @Column(name = "first_response_at") private LocalDateTime firstResponseAt;
    // When the current SLA clock started: creation, or the latest reopen. Null on tickets that
    // predate the column — read through SlaCalculator.slaStart, which falls back to createdAt.
    @Column(name = "sla_started_at") private LocalDateTime slaStartedAt;
    // Stamped by SlaMonitor the first time a deadline is found blown, so each breach
    // escalates exactly once and reporting can filter on it without recomputing.
    @Column(name = "response_breached_at") private LocalDateTime responseBreachedAt;
    @Column(name = "resolution_breached_at") private LocalDateTime resolutionBreachedAt;
    @Column(name = "resolved_at") private LocalDateTime resolvedAt;
    @Column(name = "closed_at") private LocalDateTime closedAt;

    // ON_HOLD (V11). All four are cleared/updated together by TicketService when a hold ends.
    @Enumerated(EnumType.STRING)
    @Column(name = "hold_reason", length = 30)
    private HoldReason holdReason;
    /** Optional date shown on the ticket (e.g. the booked visit). Display only for now. */
    @Column(name = "hold_until") private LocalDateTime holdUntil;
    /** When the current hold started — the moment the resolution clock stopped. Null when not on hold. */
    @Column(name = "sla_paused_at") private LocalDateTime slaPausedAt;
    /** Working minutes spent on hold during the current SLA clock (reset on reopen). */
    @Column(name = "sla_paused_minutes", nullable = false)
    private long slaPausedMinutes = 0;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStepsToReproduce() { return stepsToReproduce; }
    public void setStepsToReproduce(String stepsToReproduce) { this.stepsToReproduce = stepsToReproduce; }
    public TicketCategory getCategory() { return category; }
    public void setCategory(TicketCategory category) { this.category = category; }
    public TicketPriority getPriority() { return priority; }
    public void setPriority(TicketPriority priority) { this.priority = priority; }
    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public User getRequester() { return requester; }
    public void setRequester(User requester) { this.requester = requester; }
    public User getAssignee() { return assignee; }
    public void setAssignee(User assignee) { this.assignee = assignee; }
    public LocalDateTime getResponseDueAt() { return responseDueAt; }
    public void setResponseDueAt(LocalDateTime v) { this.responseDueAt = v; }
    public LocalDateTime getResolutionDueAt() { return resolutionDueAt; }
    public void setResolutionDueAt(LocalDateTime v) { this.resolutionDueAt = v; }
    public LocalDateTime getSlaStartedAt() { return slaStartedAt; }
    public void setSlaStartedAt(LocalDateTime v) { this.slaStartedAt = v; }
    public LocalDateTime getFirstResponseAt() { return firstResponseAt; }
    public void setFirstResponseAt(LocalDateTime v) { this.firstResponseAt = v; }
    public LocalDateTime getResponseBreachedAt() { return responseBreachedAt; }
    public void setResponseBreachedAt(LocalDateTime v) { this.responseBreachedAt = v; }
    public LocalDateTime getResolutionBreachedAt() { return resolutionBreachedAt; }
    public void setResolutionBreachedAt(LocalDateTime v) { this.resolutionBreachedAt = v; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime v) { this.resolvedAt = v; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime v) { this.closedAt = v; }
    public HoldReason getHoldReason() { return holdReason; }
    public void setHoldReason(HoldReason v) { this.holdReason = v; }
    public LocalDateTime getHoldUntil() { return holdUntil; }
    public void setHoldUntil(LocalDateTime v) { this.holdUntil = v; }
    public LocalDateTime getSlaPausedAt() { return slaPausedAt; }
    public void setSlaPausedAt(LocalDateTime v) { this.slaPausedAt = v; }
    public long getSlaPausedMinutes() { return slaPausedMinutes; }
    public void setSlaPausedMinutes(long v) { this.slaPausedMinutes = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
