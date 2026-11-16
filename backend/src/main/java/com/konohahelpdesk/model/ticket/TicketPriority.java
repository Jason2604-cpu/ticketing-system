package com.konohahelpdesk.model.ticket;

import com.konohahelpdesk.model.plan.SlaPriority;

public enum TicketPriority {
    CRITICAL(SlaPriority.CRITICAL),
    HIGH(SlaPriority.HIGH),
    MEDIUM(SlaPriority.NORMAL),
    LOW(SlaPriority.NORMAL);

    private final SlaPriority slaPriority;

    TicketPriority(SlaPriority slaPriority) { this.slaPriority = slaPriority; }

    /** Which SLA row (from the company's subscription) governs this ticket priority. */
    public SlaPriority slaPriority() { return slaPriority; }
}
