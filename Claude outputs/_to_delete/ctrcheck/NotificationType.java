package com.konohahelpdesk.model.notification;

public enum NotificationType {
    /** Sent to the engineer/admin who was just assigned a ticket. */
    TICKET_ASSIGNED_TO_YOU,
    /** Sent to the ticket's creator when staff assign someone to it. */
    TICKET_ASSIGNED_NOTICE,
    /** Sent to the other side (customer <-> assigned engineer) when a public reply is posted. */
    TICKET_REPLY,
    /** Sent to the ticket's creator when it's marked resolved. */
    TICKET_RESOLVED,
    /** Sent to the ticket's creator when it's closed. */
    TICKET_CLOSED,
    /** Sent to a company's main engineer when a new ticket comes in for their company. */
    TICKET_CREATED,
    /** Sent to the assignee / main engineer / admins when a ticket blows an SLA deadline. */
    SLA_BREACH,
    /** Sent to the contract owner / main engineer / admins as a service contract nears its end date. */
    CONTRACT_RENEWAL_DUE
}
