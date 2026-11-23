package com.konohahelpdesk.dto.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CommentRequest {
    @NotBlank @Size(max = 10_000)
    private String body;
    /** Staff only: hidden from the customer. */
    private boolean internal = false;
    /** Optional: the comment being replied to. Same ticket, not a system entry (checked in TicketService). */
    private Long replyToId;

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public boolean isInternal() { return internal; }
    public void setInternal(boolean internal) { this.internal = internal; }
    public Long getReplyToId() { return replyToId; }
    public void setReplyToId(Long replyToId) { this.replyToId = replyToId; }
}
