package com.konohahelpdesk.model.contract;

/**
 * How often a recurring contract's scheduled work comes round. The interval is expressed in
 * months rather than days so a quarterly PM visit always lands on the same day of the month
 * (15 Jan → 15 Apr → 15 Jul), which is how service contracts are actually written.
 *
 * ONE_OFF exists for contracts that carry a single dated obligation (an annual audit, a
 * certificate renewal) — the schedule is generated with exactly one visit.
 */
public enum ContractFrequency {
    ONE_OFF("One-off", 0),
    MONTHLY("Monthly", 1),
    QUARTERLY("Quarterly", 3),
    SEMI_ANNUAL("Every 6 months", 6),
    ANNUAL("Annually", 12);

    private final String label;
    private final int months;

    ContractFrequency(String label, int months) {
        this.label = label;
        this.months = months;
    }

    public String getLabel() { return label; }

    /** Months between consecutive visits. 0 for ONE_OFF, which only ever generates one. */
    public int getMonths() { return months; }

    /** How many visits fit in a 12-month term — the sensible default occurrence count. */
    public int defaultOccurrences() {
        return months == 0 ? 1 : 12 / months;
    }
}
