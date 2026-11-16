package com.konohahelpdesk.dto.kb;

import jakarta.validation.constraints.NotNull;

/** What a reader did with a suggested article on the new-ticket form — recorded in the audit log. */
public record KbFeedbackRequest(@NotNull Event event) {
    public enum Event {
        /** Opened a suggestion from the ticket form. */
        SUGGESTION_OPENED,
        /** Pressed "This solved it" and dropped the ticket they were writing. */
        TICKET_AVOIDED
    }
}
