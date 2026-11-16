package com.konohahelpdesk.dto.asset;

import com.konohahelpdesk.model.asset.Asset;
import com.konohahelpdesk.model.asset.AssetAssignment;
import com.konohahelpdesk.model.asset.AssetStatus;
import com.konohahelpdesk.model.asset.AssetType;
import com.konohahelpdesk.model.asset.WarrantyState;
import com.konohahelpdesk.model.user.User;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An asset as the API returns it.
 *
 * Everything warranty-related is computed here from {@code today} rather than read from a column,
 * so a row can never disagree with the calendar. The label fields are precomputed for the same
 * reason the contract response precomputes its own: the alternative is every client re-deriving
 * them and drifting.
 */
public class AssetResponse {

    private Long id;
    private Long companyId;
    private String companyName;

    private String assetTag;
    private String name;
    private AssetType type;
    private String typeLabel;
    private String make;
    private String model;
    private String serialNumber;
    private String location;
    private AssetStatus status;
    private String statusLabel;

    /** Which of the three assignment modes this asset is in. */
    private AssetAssignment assignedTo;
    /** The mode's own name, e.g. "A user in the system". */
    private String assignedToLabel;
    /**
     * Who actually uses it, already resolved: the owner's name, the guest's name, or "Shared".
     * One string so the table renders a column instead of branching three ways in the UI.
     */
    private String usedBy;

    private Long ownerId;
    private String ownerName;
    private String ownerEmail;
    /**
     * The owner exists but can no longer sign in. Surfaced rather than cleaned up: "what hardware
     * do we need back from this person" is exactly the offboarding question, and nulling the link
     * at the moment it becomes useful would be backwards.
     */
    private boolean ownerInactive;

    private String guestName;
    private String guestEmail;

    private LocalDate purchaseDate;
    private LocalDate warrantyStart;
    private LocalDate warrantyEnd;
    private WarrantyState warrantyState;
    private String warrantyStateLabel;
    /** Negative once the warranty has run out; null when no end date is recorded. */
    private Long daysToWarrantyEnd;

    private String notes;
    private boolean warrantyReminderEnabled;
    private Integer warrantyReminderDays;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AssetResponse of(Asset a, LocalDate today, int expiringSoonDays) {
        AssetResponse r = new AssetResponse();
        r.id = a.getId();
        r.companyId = a.getCompany().getId();
        r.companyName = a.getCompany().getName();

        r.assetTag = a.getAssetTag();
        r.name = a.getName();
        r.type = a.getType();
        r.typeLabel = a.getType() == null ? null : a.getType().getLabel();
        r.make = a.getMake();
        r.model = a.getModel();
        r.serialNumber = a.getSerialNumber();
        r.location = a.getLocation();
        r.status = a.getStatus();
        r.statusLabel = a.getStatus() == null ? null : a.getStatus().getLabel();

        r.assignedTo = a.getAssignedTo();
        r.assignedToLabel = a.getAssignedTo() == null ? null : a.getAssignedTo().getLabel();
        r.usedBy = a.usedByLabel();

        User owner = a.getOwner();
        r.ownerId = owner == null ? null : owner.getId();
        r.ownerName = owner == null ? null : (owner.getFirstName() + " " + owner.getLastName()).trim();
        r.ownerEmail = owner == null ? null : owner.getEmail();
        r.ownerInactive = a.ownerInactive();

        r.guestName = a.getGuestName();
        r.guestEmail = a.getGuestEmail();

        r.purchaseDate = a.getPurchaseDate();
        r.warrantyStart = a.getWarrantyStart();
        r.warrantyEnd = a.getWarrantyEnd();
        WarrantyState ws = a.warrantyState(today, expiringSoonDays);
        r.warrantyState = ws;
        r.warrantyStateLabel = ws.getLabel();
        r.daysToWarrantyEnd = a.daysToWarrantyEnd(today);

        r.notes = a.getNotes();
        r.warrantyReminderEnabled = a.isWarrantyReminderEnabled();
        r.warrantyReminderDays = a.getWarrantyReminderDays();

        r.createdAt = a.getCreatedAt();
        r.updatedAt = a.getUpdatedAt();
        return r;
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public String getCompanyName() { return companyName; }
    public String getAssetTag() { return assetTag; }
    public String getName() { return name; }
    public AssetType getType() { return type; }
    public String getTypeLabel() { return typeLabel; }
    public String getMake() { return make; }
    public String getModel() { return model; }
    public String getSerialNumber() { return serialNumber; }
    public String getLocation() { return location; }
    public AssetStatus getStatus() { return status; }
    public String getStatusLabel() { return statusLabel; }
    public AssetAssignment getAssignedTo() { return assignedTo; }
    public String getAssignedToLabel() { return assignedToLabel; }
    public String getUsedBy() { return usedBy; }
    public Long getOwnerId() { return ownerId; }
    public String getOwnerName() { return ownerName; }
    public String getOwnerEmail() { return ownerEmail; }
    public boolean isOwnerInactive() { return ownerInactive; }
    public String getGuestName() { return guestName; }
    public String getGuestEmail() { return guestEmail; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public LocalDate getWarrantyStart() { return warrantyStart; }
    public LocalDate getWarrantyEnd() { return warrantyEnd; }
    public WarrantyState getWarrantyState() { return warrantyState; }
    public String getWarrantyStateLabel() { return warrantyStateLabel; }
    public Long getDaysToWarrantyEnd() { return daysToWarrantyEnd; }
    public String getNotes() { return notes; }
    public boolean isWarrantyReminderEnabled() { return warrantyReminderEnabled; }
    public Integer getWarrantyReminderDays() { return warrantyReminderDays; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
