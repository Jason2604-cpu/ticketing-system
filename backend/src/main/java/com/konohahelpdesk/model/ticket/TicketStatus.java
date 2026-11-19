package com.konohahelpdesk.model.ticket;

public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    /**
     * Waiting on something outside the engineer's hands (a spare part, another vendor, a booked
     * visit, the customer). Still unfinished work, but the RESOLUTION SLA clock is paused — see
     * {@link Ticket#getSlaPausedAt()} and TicketService.putOnHold / releaseHold.
     */
    ON_HOLD,
    RESOLVED,
    CLOSED
}
