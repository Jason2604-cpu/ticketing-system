package com.konohahelpdesk.model.contract;

import com.konohahelpdesk.model.ticket.Ticket;
import com.konohahelpdesk.model.user.User;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One dated occurrence of a {@link ServiceContract} — the March PM visit, this quarter's audit.
 *
 * {@code sequence} is the position in the generated series (1-based) and is kept even after a
 * visit is rescheduled, so "visit 3 of 4" still reads correctly when someone moves it a fortnight.
 */
@Entity
@Table(name = "contract_visits", indexes = {
    @Index(name = "idx_visit_contract", columnList = "contract_id"),
    @Index(name = "idx_visit_scheduled", columnList = "scheduled_date")
})
public class ContractVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private ServiceContract contract;

    /** 1-based position in the generated series. Survives rescheduling. */
    @Column(nullable = false)
    private int sequence;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VisitStatus status = VisitStatus.SCHEDULED;

    @Column(columnDefinition = "text")
    private String notes;

    /**
     * The ticket raised for this visit, when the contract has auto-ticketing on. Non-null is also
     * the sweep's "already generated" marker, so a restart can't produce duplicate tickets.
     * Deliberately not cascaded: closing a contract must never delete a real ticket.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by")
    private User completedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** Scheduled, still not done, and the date has passed. Derived — never stored. */
    public boolean isOverdue(LocalDate today) {
        return status == VisitStatus.SCHEDULED && scheduledDate.isBefore(today);
    }

    public Long getId() { return id; }
    public ServiceContract getContract() { return contract; }
    public void setContract(ServiceContract contract) { this.contract = contract; }
    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    public VisitStatus getStatus() { return status; }
    public void setStatus(VisitStatus status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Ticket getTicket() { return ticket; }
    public void setTicket(Ticket ticket) { this.ticket = ticket; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public User getCompletedBy() { return completedBy; }
    public void setCompletedBy(User completedBy) { this.completedBy = completedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
