package com.konohahelpdesk.model.contract;

/** State of one scheduled occurrence on a contract. */
public enum VisitStatus {
    /** Not yet carried out. Overdue-ness is derived from the date, not stored. */
    SCHEDULED,
    /** Carried out and signed off. */
    COMPLETED,
    /** Deliberately not carried out (site closed, customer deferred) — kept for the audit trail. */
    SKIPPED
}
