package com.konohahelpdesk.model.asset;

import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * A serviceable thing at a customer site — a server, a firewall, a laptop, a printer.
 *
 * This is a helpdesk's asset register, not an ITAM system: there is no purchase cost, supplier,
 * depreciation or transfer history here, because none of that helps answer the three questions
 * the module exists for — what is this ticket about, what does this contract cover, and what is
 * about to fall out of warranty.
 *
 * Nothing time-dependent is stored. {@link #warrantyState} is computed from today's date every
 * time it is read, for the same reason {@code ServiceContract} derives its state rather than
 * persisting it.
 */
@Entity
@Table(name = "assets", indexes = {
    @Index(name = "ix_assets_company", columnList = "company_id"),
    @Index(name = "ix_assets_warranty_end", columnList = "warranty_end"),
    @Index(name = "ix_assets_serial", columnList = "serial_number"),
    @Index(name = "ix_assets_owner", columnList = "owner_id"),
    @Index(name = "ix_assets_group", columnList = "group_id")
})
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Whose kit it is. Always set — an asset belonging to nobody isn't one. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /**
     * Your own reference for it, e.g. "MTS-SRV-0042". Optional, and unique per company rather
     * than globally — two customers may each run their own numbering and neither should have to
     * care about the other's.
     */
    @Column(name = "asset_tag", length = 60)
    private String assetTag;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30,
            columnDefinition = "varchar(30) not null default 'OTHER'")
    private AssetType type = AssetType.OTHER;

    @Column(length = 80)
    private String make;

    @Column(length = 80)
    private String model;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    /** "DC Rack B3", "HQ Level 4 Server Room". Free text on purpose — a Site entity is a second module. */
    @Column(length = 150)
    private String location;

    /**
     * The asset group it belongs to, if any — chosen on the asset form. Always a group of the
     * same company (AssetService enforces it). A contract covering the group covers this asset.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private AssetGroup group;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30,
            columnDefinition = "varchar(30) not null default 'ACTIVE'")
    private AssetStatus status = AssetStatus.ACTIVE;

    // ── Who uses it — see AssetAssignment for why this is three fields, not one ──────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_to", nullable = false, length = 30,
            columnDefinition = "varchar(30) not null default 'SHARED'")
    private AssetAssignment assignedTo = AssetAssignment.SHARED;

    /**
     * Set only when {@link #assignedTo} is SYSTEM_USER.
     *
     * Deliberately NOT cleared when that user is deactivated or soft-deleted. "What hardware do we
     * need back from this person?" is exactly the question asked at offboarding, and nulling the
     * link at the moment it becomes useful is backwards. {@code ownerInactive} on the response
     * flags it instead.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    /** Set only when {@link #assignedTo} is GUEST — a person with no login here. */
    @Column(name = "guest_name", length = 120)
    private String guestName;

    /**
     * Optional contact address for a guest. Never a login, never becomes a {@link User}, and
     * never mailed by the warranty sweep — it is unverified text somebody typed into a spreadsheet.
     */
    @Column(name = "guest_email", length = 190)
    private String guestEmail;

    // ── Dates ────────────────────────────────────────────────────────────────────────────────

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "warranty_start")
    private LocalDate warrantyStart;

    @Column(name = "warranty_end")
    private LocalDate warrantyEnd;

    @Column(columnDefinition = "text")
    private String notes;

    // ── Warranty reminder ────────────────────────────────────────────────────────────────────

    /** Per-asset opt-in. Off by default, or a bulk import would arm four hundred emails at once. */
    @Column(name = "warranty_reminder_enabled", nullable = false,
            columnDefinition = "boolean not null default false")
    private boolean warrantyReminderEnabled = false;

    /** How many days before {@link #warrantyEnd} to send it. Null while the toggle is off. */
    @Column(name = "warranty_reminder_days")
    private Integer warrantyReminderDays;

    /**
     * The {@code warrantyEnd} the reminder was last sent for. Idempotence marker, the same shape
     * as {@code ServiceContract.renewalReminderSentFor}: the sweep only mails when this doesn't
     * match the current end date, so extending a warranty re-arms the reminder while a restart or
     * a second sweep on the same day does not resend it.
     */
    @Column(name = "warranty_reminder_sent_for")
    private LocalDate warrantyReminderSentFor;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    // ── Derived ──────────────────────────────────────────────────────────────────────────────

    /**
     * The warranty resolved against a date. Never stored.
     *
     * NONE and EXPIRED are kept apart: "we have no warranty date for this" is a gap in the record,
     * "this went out of warranty in March" is a fact about the asset, and a renewal conversation
     * only follows from the second.
     */
    public WarrantyState warrantyState(LocalDate today, int expiringSoonDays) {
        if (warrantyEnd == null) return WarrantyState.NONE;
        if (warrantyEnd.isBefore(today)) return WarrantyState.EXPIRED;
        if (!warrantyEnd.isAfter(today.plusDays(expiringSoonDays))) return WarrantyState.EXPIRING_SOON;
        return WarrantyState.IN_WARRANTY;
    }

    /** Negative once the warranty has run out; null when there is no end date to count to. */
    public Long daysToWarrantyEnd(LocalDate today) {
        return warrantyEnd == null ? null : ChronoUnit.DAYS.between(today, warrantyEnd);
    }

    /**
     * One string for "who uses this", so the list renders a column instead of branching three
     * ways in the UI. Falls back to the mode's own label if a row somehow has neither field —
     * the service's invariants prevent it, but a display helper shouldn't be the thing that throws.
     */
    public String usedByLabel() {
        return switch (assignedTo) {
            case SYSTEM_USER -> owner == null ? AssetAssignment.SYSTEM_USER.getLabel() : fullName(owner);
            case GUEST -> guestName == null || guestName.isBlank() ? AssetAssignment.GUEST.getLabel() : guestName;
            case SHARED -> AssetAssignment.SHARED.getLabel();
        };
    }

    /** True when this asset points at a user who can no longer sign in. Display + filter only. */
    public boolean ownerInactive() {
        return owner != null && owner.getStatus() != UserStatus.ACTIVE;
    }

    private static String fullName(User u) {
        String n = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                  + (u.getLastName() == null ? "" : u.getLastName())).trim();
        return n.isEmpty() ? (u.getEmail() == null ? "user#" + u.getId() : u.getEmail()) : n;
    }

    // ── Accessors ────────────────────────────────────────────────────────────────────────────

    public Long getId() { return id; }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public String getAssetTag() { return assetTag; }
    public void setAssetTag(String assetTag) { this.assetTag = assetTag; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public AssetType getType() { return type; }
    public void setType(AssetType type) { this.type = type; }

    public String getMake() { return make; }
    public void setMake(String make) { this.make = make; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public AssetGroup getGroup() { return group; }
    public void setGroup(AssetGroup group) { this.group = group; }

    public AssetStatus getStatus() { return status; }
    public void setStatus(AssetStatus status) { this.status = status; }

    public AssetAssignment getAssignedTo() { return assignedTo; }
    public void setAssignedTo(AssetAssignment assignedTo) { this.assignedTo = assignedTo; }

    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }

    public String getGuestName() { return guestName; }
    public void setGuestName(String guestName) { this.guestName = guestName; }

    public String getGuestEmail() { return guestEmail; }
    public void setGuestEmail(String guestEmail) { this.guestEmail = guestEmail; }

    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }

    public LocalDate getWarrantyStart() { return warrantyStart; }
    public void setWarrantyStart(LocalDate warrantyStart) { this.warrantyStart = warrantyStart; }

    public LocalDate getWarrantyEnd() { return warrantyEnd; }
    public void setWarrantyEnd(LocalDate warrantyEnd) { this.warrantyEnd = warrantyEnd; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isWarrantyReminderEnabled() { return warrantyReminderEnabled; }
    public void setWarrantyReminderEnabled(boolean warrantyReminderEnabled) { this.warrantyReminderEnabled = warrantyReminderEnabled; }

    public Integer getWarrantyReminderDays() { return warrantyReminderDays; }
    public void setWarrantyReminderDays(Integer warrantyReminderDays) { this.warrantyReminderDays = warrantyReminderDays; }

    public LocalDate getWarrantyReminderSentFor() { return warrantyReminderSentFor; }
    public void setWarrantyReminderSentFor(LocalDate warrantyReminderSentFor) { this.warrantyReminderSentFor = warrantyReminderSentFor; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
