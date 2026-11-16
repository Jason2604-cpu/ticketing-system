package com.konohahelpdesk.dto.contract;

import com.konohahelpdesk.model.contract.VisitStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Update one scheduled occurrence: mark it done/skipped, move it, or annotate it. */
public class VisitRequest {

    @NotNull
    private VisitStatus status = VisitStatus.SCHEDULED;

    /** Null leaves the date alone. */
    private LocalDate scheduledDate;

    private String notes;

    public VisitStatus getStatus() { return status; }
    public void setStatus(VisitStatus status) { this.status = status; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
