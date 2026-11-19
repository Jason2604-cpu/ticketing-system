package com.konohahelpdesk.dto.ticket;

import com.konohahelpdesk.model.ticket.HoldReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Body of PATCH /api/tickets/{id}/hold. */
public class HoldRequest {

    @NotNull
    private HoldReason reason;

    /** Why, in the engineer's words. Saved as an INTERNAL comment — customers only see the reason. */
    @NotBlank @Size(max = 5_000)
    private String note;

    /** Optional, shown on the ticket (e.g. the booked visit). Must be in the future when given. */
    private LocalDateTime until;

    public HoldReason getReason() { return reason; }
    public void setReason(HoldReason reason) { this.reason = reason; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getUntil() { return until; }
    public void setUntil(LocalDateTime until) { this.until = until; }
}
