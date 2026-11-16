package com.konohahelpdesk.model.contract;

/**
 * What kind of obligation the contract is — which decides whether it has a visit schedule at all.
 *
 * Not every contract is a calendar. A support retainer or a block-of-hours agreement is a real,
 * renewable commercial commitment with a term, an owner and a renewal date, but nothing is
 * scheduled: the work arrives as tickets. Forcing one of those to declare a frequency and an
 * occurrence count would put fictional dates on the schedule and fictional overdue visits on the
 * dashboard.
 *
 * TICKET_BASED does not mean "no ticket support" on the other kind — a scheduled PM contract's
 * customer raises tickets too, through their subscription like everyone else. It means "the
 * obligation is measured in tickets answered, not visits made".
 */
public enum ContractType {
    /** Preventive maintenance, inspections, certifications — dated visits, generated up front. */
    SCHEDULED_VISITS("Scheduled visits"),
    /** Support retainers, block-of-hours, standby cover — no schedule; work arrives as tickets. */
    TICKET_BASED("Ticket-based support");

    private final String label;

    ContractType(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }

    public boolean hasSchedule() {
        return this == SCHEDULED_VISITS;
    }
}
