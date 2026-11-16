package com.konohahelpdesk.model.ticket;

/**
 * How a ticket stands against one of its two SLA clocks (first response, resolution).
 * Derived on read from the due date + what actually happened — the persisted
 * {@code *BreachedAt} stamps only exist so each breach escalates once.
 */
public enum SlaState {
    /** The company's plan sets no target for this priority. */
    NONE,
    /** The target was hit inside the window. */
    MET,
    /** Still running, comfortably inside the window. */
    ON_TRACK,
    /** Still running, but past app.sla.at-risk-percent of the window. */
    AT_RISK,
    /** The deadline passed without the target being hit. */
    BREACHED
}
