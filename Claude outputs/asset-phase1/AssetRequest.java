package com.konohahelpdesk.dto.asset;

import com.konohahelpdesk.model.asset.AssetAssignment;
import com.konohahelpdesk.model.asset.AssetStatus;
import com.konohahelpdesk.model.asset.AssetType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Create/update payload for an asset.
 *
 * Note what is NOT here: {@code contractIds}. Contract coverage is edited from the contract, by
 * whoever holds MANAGE_CONTRACTS — the asset side links to it and never writes it. Adding the
 * field here would put a second, differently-privileged door onto the same join table.
 *
 * The cross-field assignment rules (SYSTEM_USER needs an owner, GUEST needs a name) are enforced
 * in AssetService rather than by annotations, because they also have to clear the fields the
 * chosen mode doesn't use — validation can reject, but it can't tidy.
 */
public class AssetRequest {

    @NotNull(message = "Company is required")
    private Long companyId;

    @NotBlank(message = "Asset name is required")
    @Size(max = 150)
    private String name;

    @Size(max = 60)
    private String assetTag;

    @NotNull
    private AssetType type = AssetType.OTHER;

    @Size(max = 80) private String make;
    @Size(max = 80) private String model;
    @Size(max = 100) private String serialNumber;
    @Size(max = 150) private String location;

    @NotNull
    private AssetStatus status = AssetStatus.ACTIVE;

    /** Defaults to SHARED so a firewall or printer is one click — see AssetAssignment. */
    @NotNull
    private AssetAssignment assignedTo = AssetAssignment.SHARED;

    /** Required when assignedTo is SYSTEM_USER, ignored otherwise. */
    private Long ownerId;

    /** Required when assignedTo is GUEST, ignored otherwise. */
    @Size(max = 120)
    private String guestName;

    @Email(message = "That doesn't look like an email address")
    @Size(max = 190)
    private String guestEmail;

    private LocalDate purchaseDate;
    private LocalDate warrantyStart;
    private LocalDate warrantyEnd;

    private String notes;

    private boolean warrantyReminderEnabled = false;

    @Min(1) @Max(365)
    private Integer warrantyReminderDays;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAssetTag() { return assetTag; }
    public void setAssetTag(String assetTag) { this.assetTag = assetTag; }
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
    public AssetStatus getStatus() { return status; }
    public void setStatus(AssetStatus status) { this.status = status; }
    public AssetAssignment getAssignedTo() { return assignedTo; }
    public void setAssignedTo(AssetAssignment assignedTo) { this.assignedTo = assignedTo; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
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
}
