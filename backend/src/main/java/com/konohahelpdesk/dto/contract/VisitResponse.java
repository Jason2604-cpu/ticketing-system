package com.konohahelpdesk.dto.contract;

import com.konohahelpdesk.model.contract.ContractVisit;
import com.konohahelpdesk.model.contract.VisitStatus;
import com.konohahelpdesk.model.user.User;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One occurrence on a contract's schedule. */
public class VisitResponse {

    private Long id;
    private int sequence;
    private LocalDate scheduledDate;
    private VisitStatus status;
    private boolean overdue;
    private String notes;
    private Long ticketId;
    private LocalDateTime completedAt;
    private String completedByName;

    public static VisitResponse of(ContractVisit v, LocalDate today) {
        VisitResponse r = new VisitResponse();
        r.id = v.getId();
        r.sequence = v.getSequence();
        r.scheduledDate = v.getScheduledDate();
        r.status = v.getStatus();
        r.overdue = v.isOverdue(today);
        r.notes = v.getNotes();
        r.ticketId = v.getTicket() == null ? null : v.getTicket().getId();
        r.completedAt = v.getCompletedAt();
        User by = v.getCompletedBy();
        r.completedByName = by == null ? null : by.getFirstName() + " " + by.getLastName();
        return r;
    }

    public Long getId() { return id; }
    public int getSequence() { return sequence; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public VisitStatus getStatus() { return status; }
    public boolean isOverdue() { return overdue; }
    public String getNotes() { return notes; }
    public Long getTicketId() { return ticketId; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public String getCompletedByName() { return completedByName; }
}
