package com.konohahelpdesk.model.contract;

import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.ticket.TicketCategory;
import com.konohahelpdesk.model.ticket.TicketPriority;
import com.konohahelpdesk.model.user.User;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A recurring obligation to a customer company — a preventive-maintenance schedule, a support
 * retainer, an annual certification — with its occurrences generated up front as
 * {@link ContractVisit} rows.
 *
 * Why the visits are materialised rather than computed on the fly: they are things people act on
 * individually. A visit gets rescheduled, skipped, signed off by a named engineer, and (when
 * {@code autoCreateTicket} is on) linked to the ticket it generated. None of that has anywhere to
 * live if "next due date" is just arithmetic over a start date.
 */
@Entity
@Table(name = "service_contracts", indexes = {
    @Index(name = "idx_contract_company", columnList = "company_id"),
    @Index(name = "idx_contract_end_date", columnList = "end_date")
})
public class ServiceContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Who the contract is with. Always set — a contract with nobody isn't one. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 150)
    private String name;

    /** The customer's or your own reference for it, e.g. "MTS-PM-2026-014". Free text, optional. */
    @Column(name = "contract_number", length = 60)
    private String contractNumber;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContractStatus status = ContractStatus.ACTIVE;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContractFrequency frequency = ContractFrequency.ANNUAL;

    /** How many visits to generate. Defaults from the frequency but overridable per contract. */
    @Column(name = "occurrences", nullable = false)
    private int occurrences = 1;

    /** When the first occurrence falls. Usually the start date, but a contract can start mid-cycle. */
    @Column(name = "first_visit_date", nullable = false)
    private LocalDate firstVisitDate;

    /** Equipment / serials this contract covers. Free text today; see the note in ContractService. */
    @Column(name = "covered_assets", columnDefinition = "text")
    private String coveredAssets;

    /** Staff member responsible for renewing it. Null means nobody has been named yet. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    /** Days before {@code endDate} to email the renewal reminder. Null disables the reminder. */
    @Column(name = "renewal_reminder_days")
    private Integer renewalReminderDays;

    /**
     * The {@code endDate} the renewal reminder was last sent for. Idempotence marker: the daily
     * sweep only mails when this doesn't match the current end date, so extending a contract
     * re-arms the reminder while a restart or a second sweep the same day does not resend it.
     */
    @Column(name = "renewal_reminder_sent_for")
    private LocalDate renewalReminderSentFor;

    /** Per-contract opt-in: raise a ticket automatically as each visit comes due. */
    @Column(name = "auto_create_ticket", nullable = false)
    private boolean autoCreateTicket = false;

    /** How many days ahead of a visit to raise its ticket, so the engineer can plan. */
    @Column(name = "ticket_lead_days", nullable = false)
    private int ticketLeadDays = 7;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_priority", nullable = false, length = 20)
    private TicketPriority ticketPriority = TicketPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_category", nullable = false, length = 30)
    private TicketCategory ticketCategory = TicketCategory.TECHNICAL;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("scheduledDate asc, sequence asc")
    private List<ContractVisit> visits = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Status resolved against today. CANCELLED wins outright; otherwise the end date decides,
     * with {@code expiringSoonDays} (a config value, passed in rather than hard-coded) marking
     * the window in which a renewal conversation should already be happening.
     */
    public ContractState derivedState(LocalDate today, int expiringSoonDays) {
        if (status == ContractStatus.CANCELLED) return ContractState.CANCELLED;
        if (endDate.isBefore(today)) return ContractState.EXPIRED;
        if (!endDate.isAfter(today.plusDays(expiringSoonDays))) return ContractState.EXPIRING_SOON;
        return ContractState.ACTIVE;
    }

    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContractNumber() { return contractNumber; }
    public void setContractNumber(String contractNumber) { this.contractNumber = contractNumber; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ContractStatus getStatus() { return status; }
    public void setStatus(ContractStatus status) { this.status = status; }
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
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public Integer getRenewalReminderDays() { return renewalReminderDays; }
    public void setRenewalReminderDays(Integer renewalReminderDays) { this.renewalReminderDays = renewalReminderDays; }
    public LocalDate getRenewalReminderSentFor() { return renewalReminderSentFor; }
    public void setRenewalReminderSentFor(LocalDate renewalReminderSentFor) { this.renewalReminderSentFor = renewalReminderSentFor; }
    public boolean isAutoCreateTicket() { return autoCreateTicket; }
    public void setAutoCreateTicket(boolean autoCreateTicket) { this.autoCreateTicket = autoCreateTicket; }
    public int getTicketLeadDays() { return ticketLeadDays; }
    public void setTicketLeadDays(int ticketLeadDays) { this.ticketLeadDays = ticketLeadDays; }
    public TicketPriority getTicketPriority() { return ticketPriority; }
    public void setTicketPriority(TicketPriority ticketPriority) { this.ticketPriority = ticketPriority; }
    public TicketCategory getTicketCategory() { return ticketCategory; }
    public void setTicketCategory(TicketCategory ticketCategory) { this.ticketCategory = ticketCategory; }
    public List<ContractVisit> getVisits() { return visits; }
    public void setVisits(List<ContractVisit> visits) { this.visits = visits == null ? new ArrayList<>() : visits; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
