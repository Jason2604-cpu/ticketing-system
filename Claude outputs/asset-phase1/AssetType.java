package com.konohahelpdesk.model.asset;

/**
 * What kind of thing the asset is. Deliberately a fixed list rather than free text: the whole
 * point of the field is grouping and filtering ("show me every firewall out of warranty"), and a
 * free-text field would hold "Firewall", "firewall", "FW" and "fortigate" within a month.
 *
 * Kept coarse on purpose. This is a helpdesk's view of a customer's kit, not a procurement
 * catalogue — the make and model columns carry the detail.
 */
public enum AssetType {
    SERVER("Server"),
    WORKSTATION("Workstation"),
    LAPTOP("Laptop"),
    NETWORK_DEVICE("Network device"),
    FIREWALL("Firewall"),
    STORAGE("Storage"),
    PRINTER("Printer"),
    UPS("UPS"),
    SOFTWARE_LICENSE("Software licence"),
    PERIPHERAL("Peripheral"),
    OTHER("Other");

    private final String label;

    AssetType(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }

    /**
     * Whether an asset of this type is normally used by one identifiable person. Drives nothing
     * more than the form's initial {@link AssetAssignment} — a hint to save a click, never a rule.
     */
    public boolean usuallyPersonal() {
        return this == LAPTOP || this == WORKSTATION;
    }
}
