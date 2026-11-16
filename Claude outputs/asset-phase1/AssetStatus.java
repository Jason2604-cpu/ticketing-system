package com.konohahelpdesk.model.asset;

/**
 * Where the asset is in its working life. Stored, and set by a human.
 *
 * Orthogonal to {@link WarrantyState} on purpose: a RETIRED box can still be inside its warranty,
 * and an ACTIVE one is usually the one that just fell out of it. Conflating the two into a single
 * "status" is how you end up unable to answer either question.
 */
public enum AssetStatus {
    /** In service. */
    ACTIVE("Active"),
    /** Out of service temporarily — with the vendor, on the bench, awaiting a part. */
    IN_REPAIR("In repair"),
    /** Working but not deployed — a spare on the shelf. */
    IN_STORAGE("In storage"),
    /** Taken out of service, kept on the register for history. */
    RETIRED("Retired"),
    /** Physically gone — scrapped, returned, sold. Kept so old tickets still resolve. */
    DISPOSED("Disposed");

    private final String label;

    AssetStatus(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }

    /** Retired and disposed kit is excluded from the warranty sweep — nobody renews a scrapped box. */
    public boolean isInService() {
        return this != RETIRED && this != DISPOSED;
    }
}
