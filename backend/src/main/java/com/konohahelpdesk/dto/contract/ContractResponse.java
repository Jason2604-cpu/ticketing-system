package com.konohahelpdesk.dto.contract;

import com.konohahelpdesk.dto.asset.AssetSummary;
import com.konohahelpdesk.model.asset.Asset;
import com.konohahelpdesk.model.asset.AssetGroup;
import com.konohahelpdesk.model.contract.*;
import com.konohahelpdesk.model.media.MediaFile;
import com.konohahelpdesk.model.ticket.TicketCategory;
import com.konohahelpdesk.model.ticket.TicketPriority;
import com.konohahelpdesk.model.user.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A contract as the API returns it. Everything time-dependent — state, next due date, overdue
 * counts — is computed here from {@code today} rather than stored, so a row can never disagree
 * with the calendar.
 */
public class ContractResponse {

    private Long id;
    private Long companyId;
    private String companyName;
    private String name;
    private String contractNumber;
    private String description;
    private ContractStatus status;
    private ContractType contractType;
    private String contractTypeLabel;
    private ContractState state;
    private LocalDate startDate;
    private LocalDate endDate;
    /** Negative once the contract has run out. */
    private long daysToExpiry;
    private ContractFrequency frequency;
    private String frequencyLabel;
    private int occurrences;
    private LocalDate firstVisitDate;
    private String coveredAssets;
    private Long ownerId;
    private String ownerName;
    private Integer renewalReminderDays;
    private boolean autoCreateTicket;
    private int ticketLeadDays;
    private TicketPriority ticketPriority;
    private TicketCategory ticketCategory;

    private int visitsTotal;
    private int visitsCompleted;
    private int visitsOverdue;
    /** The soonest still-scheduled visit, or null when they're all done. */
    private LocalDate nextVisitDate;

    private List<VisitResponse> visits;
    /** Detail only: assets listed individually on the contract. */
    private List<AssetSummary> coveredAssetItems;
    /** Detail only: groups covered as a unit, each with its current members. */
    private List<CoveredGroup> coveredGroups;
    /** Detail only: distinct assets covered, counting individual picks and group members once each. */
    private Integer coveredAssetTotal;
    private List<Attachment> attachments;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public record Attachment(String id, String name, String contentType, long sizeBytes, String url, LocalDateTime createdAt) {
        public static Attachment of(MediaFile m) {
            return new Attachment(m.getId(), m.getOriginalName(), m.getContentType(), m.getSizeBytes(),
                "/api/media/" + m.getId(), m.getCreatedAt());
        }
    }

    public record CoveredGroup(Long id, String name, int assetCount, List<AssetSummary> assets) {
        public static CoveredGroup of(AssetGroup g) {
            List<AssetSummary> members = g.getAssets().stream()
                .sorted(Comparator.comparing((Asset a) -> a.getName() == null ? "" : a.getName(), String.CASE_INSENSITIVE_ORDER))
                .map(AssetSummary::of)
                .toList();
            return new CoveredGroup(g.getId(), g.getName(), members.size(), members);
        }
    }

    /** Summary form: no visit rows, no attachments — what the list page needs and nothing more. */
    public static ContractResponse summary(ServiceContract c, LocalDate today, int expiringSoonDays, List<ContractVisit> visits) {
        ContractResponse r = new ContractResponse();
        r.id = c.getId();
        r.companyId = c.getCompany().getId();
        r.companyName = c.getCompany().getName();
        r.name = c.getName();
        r.contractNumber = c.getContractNumber();
        r.description = c.getDescription();
        r.status = c.getStatus();
        r.contractType = c.getContractType();
        r.contractTypeLabel = c.getContractType() == null ? null : c.getContractType().getLabel();
        r.state = c.derivedState(today, expiringSoonDays);
        r.startDate = c.getStartDate();
        r.endDate = c.getEndDate();
        r.daysToExpiry = java.time.temporal.ChronoUnit.DAYS.between(today, c.getEndDate());
        r.frequency = c.getFrequency();
        r.frequencyLabel = c.getFrequency().getLabel();
        r.occurrences = c.getOccurrences();
        r.firstVisitDate = c.getFirstVisitDate();
        r.coveredAssets = c.getCoveredAssets();
        User owner = c.getOwner();
        r.ownerId = owner == null ? null : owner.getId();
        r.ownerName = owner == null ? null : owner.getFirstName() + " " + owner.getLastName();
        r.renewalReminderDays = c.getRenewalReminderDays();
        r.autoCreateTicket = c.isAutoCreateTicket();
        r.ticketLeadDays = c.getTicketLeadDays();
        r.ticketPriority = c.getTicketPriority();
        r.ticketCategory = c.getTicketCategory();
        r.createdAt = c.getCreatedAt();
        r.updatedAt = c.getUpdatedAt();

        r.visitsTotal = visits.size();
        r.visitsCompleted = (int) visits.stream().filter(v -> v.getStatus() == VisitStatus.COMPLETED).count();
        r.visitsOverdue = (int) visits.stream().filter(v -> v.isOverdue(today)).count();
        r.nextVisitDate = visits.stream()
            .filter(v -> v.getStatus() == VisitStatus.SCHEDULED)
            .map(ContractVisit::getScheduledDate)
            .min(LocalDate::compareTo)
            .orElse(null);
        return r;
    }

    /** Detail form: the summary plus the schedule and the attached documents. */
    public static ContractResponse detail(ServiceContract c, LocalDate today, int expiringSoonDays,
                                          List<ContractVisit> visits, List<MediaFile> files,
                                          List<Asset> coveredAssets, List<AssetGroup> coveredGroups) {
        ContractResponse r = summary(c, today, expiringSoonDays, visits);
        r.coveredAssetItems = coveredAssets.stream().map(AssetSummary::of).toList();
        r.coveredGroups = coveredGroups.stream()
            .sorted(Comparator.comparing(AssetGroup::getName, String.CASE_INSENSITIVE_ORDER))
            .map(CoveredGroup::of)
            .toList();
        Set<Long> distinct = new LinkedHashSet<>();
        coveredAssets.forEach(a -> distinct.add(a.getId()));
        coveredGroups.forEach(g -> g.getAssets().forEach(a -> distinct.add(a.getId())));
        r.coveredAssetTotal = distinct.size();
        r.visits = visits.stream().map(v -> VisitResponse.of(v, today)).toList();
        r.attachments = files.stream().map(Attachment::of).toList();
        return r;
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public String getCompanyName() { return companyName; }
    public String getName() { return name; }
    public String getContractNumber() { return contractNumber; }
    public String getDescription() { return description; }
    public ContractStatus getStatus() { return status; }
    public ContractType getContractType() { return contractType; }
    public String getContractTypeLabel() { return contractTypeLabel; }
    public ContractState getState() { return state; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public long getDaysToExpiry() { return daysToExpiry; }
    public ContractFrequency getFrequency() { return frequency; }
    public String getFrequencyLabel() { return frequencyLabel; }
    public int getOccurrences() { return occurrences; }
    public LocalDate getFirstVisitDate() { return firstVisitDate; }
    public String getCoveredAssets() { return coveredAssets; }
    public Long getOwnerId() { return ownerId; }
    public String getOwnerName() { return ownerName; }
    public Integer getRenewalReminderDays() { return renewalReminderDays; }
    public boolean isAutoCreateTicket() { return autoCreateTicket; }
    public int getTicketLeadDays() { return ticketLeadDays; }
    public TicketPriority getTicketPriority() { return ticketPriority; }
    public TicketCategory getTicketCategory() { return ticketCategory; }
    public int getVisitsTotal() { return visitsTotal; }
    public int getVisitsCompleted() { return visitsCompleted; }
    public int getVisitsOverdue() { return visitsOverdue; }
    public LocalDate getNextVisitDate() { return nextVisitDate; }
    public List<VisitResponse> getVisits() { return visits; }
    public List<AssetSummary> getCoveredAssetItems() { return coveredAssetItems; }
    public List<CoveredGroup> getCoveredGroups() { return coveredGroups; }
    public Integer getCoveredAssetTotal() { return coveredAssetTotal; }
    public List<Attachment> getAttachments() { return attachments; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
