package com.konohahelpdesk.model.asset;

/**
 * Who an asset belongs to — an explicit three-way choice rather than a nullable owner column.
 *
 * A laptop belongs to a person. A firewall belongs to nobody in particular. A printer belongs to
 * a floor. One nullable {@code owner_id} cannot tell those apart: a null would mean both "shared
 * by design" and "nobody has filled this in yet", and those are different answers to the same
 * report. So every asset states which of the three it is, and {@code AssetService.apply()} clears
 * the fields the chosen mode doesn't use.
 *
 * Named SHARED rather than DEFAULT because in six months a column full of "DEFAULT" tells you
 * nothing, while "shared" is a positive statement about the asset.
 */
public enum AssetAssignment {
    /** Used by one person who has a login here. {@code owner} is set. */
    SYSTEM_USER("A user in the system"),
    /** Used by one named person who is not a portal user. {@code guestName} is set. */
    GUEST("Someone else"),
    /** Shared infrastructure — firewalls, switches, printers, servers. Neither field is set. */
    SHARED("Shared / infrastructure");

    private final String label;

    AssetAssignment(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
