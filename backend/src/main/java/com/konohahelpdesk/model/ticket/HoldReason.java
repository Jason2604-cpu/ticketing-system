package com.konohahelpdesk.model.ticket;

/**
 * Why a ticket is {@link TicketStatus#ON_HOLD}. Customers see the label; the staff member's note
 * that goes with it is saved as an internal comment and stays staff-only.
 */
public enum HoldReason {
    SPARE_PART("Waiting for spare part"),
    VENDOR("Waiting for third-party vendor"),
    SCHEDULED("Scheduled with customer"),
    /** The only reason that ends by itself: a public reply from the customer resumes the ticket. */
    CUSTOMER_REPLY("Waiting for customer reply"),
    OTHER("Other");

    private final String label;

    HoldReason(String label) { this.label = label; }

    public String label() { return label; }
}
