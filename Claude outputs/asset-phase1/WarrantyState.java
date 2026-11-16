package com.konohahelpdesk.model.asset;

/**
 * An asset's warranty resolved against today's date. DERIVED, never stored — same reasoning as
 * {@link com.konohahelpdesk.model.contract.ContractState}: a persisted "expired" flag is wrong the
 * morning after it is written, and nothing in the system would be there to correct it.
 *
 * Computed by {@link Asset#warrantyState(java.time.LocalDate, int)}.
 */
public enum WarrantyState {
    IN_WARRANTY("In warranty"),
    EXPIRING_SOON("Expiring soon"),
    EXPIRED("Expired"),
    /** No warranty end date recorded — distinct from "expired", which is a known fact. */
    NONE("Not recorded");

    private final String label;

    WarrantyState(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
