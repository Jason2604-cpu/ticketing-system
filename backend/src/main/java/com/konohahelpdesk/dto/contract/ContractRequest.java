package com.konohahelpdesk.dto.contract;

import com.konohahelpdesk.model.contract.ContractFrequency;
import com.konohahelpdesk.model.contract.ContractStatus;
import com.konohahelpdesk.model.contract.ContractType;
import com.konohahelpdesk.model.ticket.TicketCategory;
import com.konohahelpdesk.model.ticket.TicketPriority;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Create/update payload for a recurring service contract. */
public class ContractRequest {

    @NotNull(message = "Company is required")
    private Long companyId;

    @NotBlank(message = "Contract name is required")
    @Size(max = 150)
    private String name;

    @Size(max = 60)
    private String contractNumber;

    private String description;

    @NotNull
    private ContractStatus status = ContractStatus.ACTIVE;

    /** SCHEDULED_VISITS generates a visit schedule; TICKET_BASED has none. */
    @NotNull
    private ContractType contractType = ContractType.SCHEDULED_VISITS;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @NotNull
    private ContractFrequency frequency = ContractFrequency.ANNUAL;

    @Min(value = 1, message = "A contract needs at least one scheduled occurrence")
    @Max(value = 120, message = "That's more than 120 occurrences — split it into separate contracts")
    private int occurrences = 1;

    /** Null falls back to the start date. */
    private LocalDate firstVisitDate;

    private String coveredAssets;

    /** Registered assets covered individually. Must belong to the contract's company. */
    private List<Long> assetIds = new ArrayList<>();

    /** Asset groups covered as a unit. Must belong to the contract's company. */
    private List<Long> assetGroupIds = new ArrayList<>();

    private Long ownerId;

    @Min(0) @Max(365)
    private Integer renewalReminderDays;

    private boolean autoCreateTicket = false;

    @Min(0) @Max(90)
    private int ticketLeadDays = 7;

    @NotNull
    private TicketPriority ticketPriority = TicketPriority.MEDIUM;

    @NotNull
    private TicketCategory ticketCategory = TicketCategory.TECHNICAL;

    /**
     * On update only: regenerate the visit schedule from the dates/frequency above. Off by
     * default, because regeneration drops every not-yet-completed visit and the notes and
     * reschedules on them — silently losing that on an unrelated edit would be indefensible.
     */
    private boolean regenerateSchedule = false;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContractNumber() { return contractNumber; }
    public void setContractNumber(String contractNumber) { this.contractNumber = contractNumber; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ContractStatus getStatus() { return status; }
    public void setStatus(ContractStatus status) { this.status = status; }
    public ContractType getContractType() { return contractType; }
    public void setContractType(ContractType contractType) { this.contractType = contractType; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public ContractFrequency getFrequency() { return frequency; }
    public void setFrequency(ContractFrequency frequency) { this.frequency = frequency; }
    public int getOccurrences() { return occurrences; }
    public void setOccurrences(int occurrences) { this.occurrences = occurrences; }
    public LocalDate getFirstVisitDate() { return firstVisitDate; }
    public void setFirstVisitDate(LocalDate firstVisitDate) { this.firstVisitDate = firstVisitDate; }
    public String getCoveredAssets() { return coveredAssets; }
    public void setCoveredAssets(String coveredAssets) { this.coveredAssets = coveredAssets; }
    public List<Long> getAssetIds() { return assetIds; }
    public void setAssetIds(List<Long> assetIds) { this.assetIds = assetIds == null ? new ArrayList<>() : assetIds; }
    public List<Long> getAssetGroupIds() { return assetGroupIds; }
    public void setAssetGroupIds(List<Long> assetGroupIds) { this.assetGroupIds = assetGroupIds == null ? new ArrayList<>() : assetGroupIds; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Integer getRenewalReminderDays() { return renewalReminderDays; }
    public void setRenewalReminderDays(Integer renewalReminderDays) { this.renewalReminderDays = renewalReminderDays; }
    public boolean isAutoCreateTicket() { return autoCreateTicket; }
    public void setAutoCreateTicket(boolean autoCreateTicket) { this.autoCreateTicket = autoCreateTicket; }
    public int getTicketLeadDays() { return ticketLeadDays; }
    public void setTicketLeadDays(int ticketLeadDays) { this.ticketLeadDays = ticketLeadDays; }
    public TicketPriority getTicketPriority() { return ticketPriority; }
    public void setTicketPriority(TicketPriority ticketPriority) { this.ticketPriority = ticketPriority; }
    public TicketCategory getTicketCategory() { return ticketCategory; }
    public void setTicketCategory(TicketCategory ticketCategory) { this.ticketCategory = ticketCategory; }
    public boolean isRegenerateSchedule() { return regenerateSchedule; }
    public void setRegenerateSchedule(boolean regenerateSchedule) { this.regenerateSchedule = regenerateSchedule; }
}
