package com.konohahelpdesk.service.contract;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.dto.contract.ContractRequest;
import com.konohahelpdesk.dto.contract.ContractResponse;
import com.konohahelpdesk.dto.contract.VisitRequest;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.exception.ResourceNotFoundException;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.contract.*;
import com.konohahelpdesk.model.media.MediaFile;
import com.konohahelpdesk.model.media.MediaKind;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.repository.company.CompanyRepository;
import com.konohahelpdesk.repository.contract.ContractVisitRepository;
import com.konohahelpdesk.repository.contract.ServiceContractRepository;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
import com.konohahelpdesk.service.media.MediaStorageService;
import com.konohahelpdesk.service.subscription.CompanySubscriptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Recurring service contracts — preventive maintenance schedules, support retainers, annual
 * certifications — and the dated visits generated from them.
 *
 * Authorization is at the controller: {@code ADMIN} or the {@code MANAGE_CONTRACTS} privilege.
 * There is deliberately no per-company scoping inside this service the way {@code TicketService}
 * has: a contract is the provider's own commercial record, and nobody who isn't provider staff
 * can reach any of these methods in the first place.
 */
@Service
public class ContractService {

    private final ServiceContractRepository contracts;
    private final ContractVisitRepository visits;
    private final CompanyRepository companies;
    private final UserRepository users;
    private final ContractScheduleGenerator generator;
    private final MediaStorageService media;
    private final CompanySubscriptionService subscriptions;
    private final AuditLogger audit;
    private final AppProperties.Contracts cfg;
    private final AppProperties.Media mediaCfg;

    public ContractService(ServiceContractRepository contracts, ContractVisitRepository visits,
                           CompanyRepository companies, UserRepository users,
                           ContractScheduleGenerator generator, MediaStorageService media,
                           CompanySubscriptionService subscriptions, AuditLogger audit, AppProperties props) {
        this.contracts = contracts;
        this.visits = visits;
        this.companies = companies;
        this.users = users;
        this.generator = generator;
        this.media = media;
        this.subscriptions = subscriptions;
        this.audit = audit;
        this.cfg = props.getContracts();
        this.mediaCfg = props.getMedia();
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    /**
     * The list, with optional filters. All three are applied in memory over one ordered load:
     * a service provider has hundreds of contracts, not millions, and the derived state a user
     * filters by (EXPIRING_SOON, EXPIRED) isn't a column, so a query couldn't filter on it anyway.
     */
    public List<ContractResponse> list(Long companyId, ContractState state, String q) {
        LocalDate today = LocalDate.now();
        String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);

        List<ServiceContract> matching = contracts.findAllForListing().stream()
            .filter(c -> companyId == null || c.getCompany().getId().equals(companyId))
            .filter(c -> state == null || c.derivedState(today, cfg.getExpiringSoonDays()) == state)
            .filter(c -> needle == null || matches(c, needle))
            .toList();
        if (matching.isEmpty()) return List.of();

        // One query for every visit on the page, not one per row — the UI polls this every 15s.
        Map<Long, List<ContractVisit>> byContract = visitsByContract(matching);
        return matching.stream()
            .map(c -> ContractResponse.summary(c, today, cfg.getExpiringSoonDays(),
                byContract.getOrDefault(c.getId(), List.of())))
            .toList();
    }

    private Map<Long, List<ContractVisit>> visitsByContract(List<ServiceContract> forContracts) {
        List<Long> ids = forContracts.stream().map(ServiceContract::getId).toList();
        return visits.findByContractIdInOrderBySequenceAsc(ids).stream()
            .collect(Collectors.groupingBy(v -> v.getContract().getId()));
    }

    private boolean matches(ServiceContract c, String needle) {
        return contains(c.getName(), needle)
            || contains(c.getContractNumber(), needle)
            || contains(c.getCompany().getName(), needle)
            || contains(c.getCoveredAssets(), needle)
            || contains(c.getDescription(), needle);
    }

    private boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    public ContractResponse get(Long id) {
        return detail(load(id));
    }


    // ── Write ────────────────────────────────────────────────────────────────

    @Transactional
    public ContractResponse create(ContractRequest req, User actor) {
        validateDates(req);
        ServiceContract c = new ServiceContract();
        apply(c, req);
        ServiceContract saved = contracts.save(c);

        visits.saveAll(generator.build(saved, 1));
        audit.event("CONTRACT_CREATED", "contractId=" + saved.getId() + " companyId=" + saved.getCompany().getId()
            + " frequency=" + saved.getFrequency());
        return detail(load(saved.getId()));
    }

    @Transactional
    public ContractResponse update(Long id, ContractRequest req, User actor) {
        validateDates(req);
        ServiceContract c = load(id);
        boolean hadSchedule = c.hasSchedule();
        apply(c, req);
        ServiceContract saved = contracts.save(c);

        // Switching a contract to ticket-based regenerates whether or not the flag was ticked:
        // leaving its old visits in place would keep them turning up as due and overdue on a
        // contract that no longer has a schedule. regenerate() keeps anything already completed,
        // skipped or ticketed, so the history of what was done under it survives.
        if (req.isRegenerateSchedule() || (hadSchedule && !saved.hasSchedule())) {
            regenerate(saved, actor);
        }
        audit.event("CONTRACT_UPDATED", "contractId=" + id + " regenerated=" + req.isRegenerateSchedule());
        return detail(load(id));
    }

    /**
     * Rebuilds the schedule from the contract's current dates, keeping every visit that already
     * happened. Completed and skipped visits are history — an engineer signed them off — so they
     * survive, and the regenerated series is numbered after them. Only not-yet-actioned visits
     * are replaced.
     */
    @Transactional
    public ContractResponse regenerateSchedule(Long id, User actor) {
        ServiceContract c = load(id);
        regenerate(c, actor);
        return detail(load(id));
    }

    private void regenerate(ServiceContract c, User actor) {
        List<ContractVisit> existing = visits.findByContractIdOrderBySequenceAsc(c.getId());
        List<ContractVisit> keep = existing.stream()
            .filter(v -> v.getStatus() != VisitStatus.SCHEDULED || v.getTicket() != null)
            .toList();
        List<ContractVisit> drop = existing.stream()
            .filter(v -> v.getStatus() == VisitStatus.SCHEDULED && v.getTicket() == null)
            .toList();

        visits.deleteAll(drop);
        // build() returns nothing for a ticket-based contract, so switching a contract's type and
        // regenerating is also how you clear a schedule that no longer applies.
        visits.saveAll(generator.build(c, keep.size() + 1));
        audit.event("CONTRACT_SCHEDULE_REGENERATED",
            "contractId=" + c.getId() + " kept=" + keep.size() + " replaced=" + drop.size());
    }

    @Transactional
    public void delete(Long id, User actor) {
        ServiceContract c = load(id);
        // Attachments are stored files, not rows — orphanRemoval can't reach them.
        for (MediaFile f : media.listFor(MediaKind.CONTRACT_ATTACHMENT, id)) {
            media.delete(f.getId());
        }
        contracts.delete(c);
        audit.event("CONTRACT_DELETED", "contractId=" + id);
    }

    // ── Visits ───────────────────────────────────────────────────────────────

    /**
     * Marks one occurrence done/skipped, moves it, or annotates it. Completion is stamped with
     * the acting user and the time; re-opening a completed visit clears both, so the sign-off
     * always names whoever actually signed off last.
     */
    @Transactional
    public ContractResponse updateVisit(Long contractId, Long visitId, VisitRequest req, User actor) {
        ContractVisit v = visits.findDetailById(visitId)
            .orElseThrow(() -> new ResourceNotFoundException("Visit not found: " + visitId));
        if (!v.getContract().getId().equals(contractId)) {
            throw new BusinessRuleException("That visit belongs to a different contract");
        }

        VisitStatus previous = v.getStatus();
        v.setStatus(req.getStatus());
        if (req.getScheduledDate() != null) v.setScheduledDate(req.getScheduledDate());
        if (req.getNotes() != null) v.setNotes(req.getNotes().isBlank() ? null : req.getNotes().trim());

        if (req.getStatus() == VisitStatus.COMPLETED && previous != VisitStatus.COMPLETED) {
            v.setCompletedAt(LocalDateTime.now());
            v.setCompletedBy(actor);
        } else if (req.getStatus() != VisitStatus.COMPLETED) {
            v.setCompletedAt(null);
            v.setCompletedBy(null);
        }
        visits.save(v);
        audit.event("CONTRACT_VISIT_UPDATED", "contractId=" + contractId + " visitId=" + visitId
            + " status=" + req.getStatus());
        return detail(load(contractId));
    }

    // ── Attachments ──────────────────────────────────────────────────────────

    @Transactional
    public ContractResponse attach(Long id, MultipartFile file, User actor) {
        ServiceContract c = load(id);
        if (media.listFor(MediaKind.CONTRACT_ATTACHMENT, id).size() >= mediaCfg.getMaxAttachmentsPerTicket()) {
            throw new BusinessRuleException("This contract already has the maximum of "
                + mediaCfg.getMaxAttachmentsPerTicket() + " documents");
        }
        Long companyId = c.getCompany().getId();
        subscriptions.assertStorageAvailable(companyId, media.usedBytesForCompany(companyId), file.getSize());
        MediaFile stored = media.storeContractDocument(file, id, actor.getId());
        audit.event("CONTRACT_DOCUMENT_ADDED", "contractId=" + id + " mediaId=" + stored.getId());
        return detail(load(id));
    }

    @Transactional
    public ContractResponse removeAttachment(Long id, String mediaId, User actor) {
        load(id);
        MediaFile f = media.get(mediaId);
        // Guards against deleting another contract's (or a ticket's) file by passing its id here.
        if (f.getKind() != MediaKind.CONTRACT_ATTACHMENT || !f.getOwnerId().equals(id)) {
            throw new BusinessRuleException("That file doesn't belong to this contract");
        }
        media.delete(mediaId);
        audit.event("CONTRACT_DOCUMENT_REMOVED", "contractId=" + id + " mediaId=" + mediaId);
        return detail(load(id));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ServiceContract load(Long id) {
        return contracts.findDetailById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Contract not found: " + id));
    }

    private ContractResponse detail(ServiceContract c) {
        return ContractResponse.detail(c, LocalDate.now(), cfg.getExpiringSoonDays(),
            visits.findByContractIdOrderBySequenceAsc(c.getId()),
            media.listFor(MediaKind.CONTRACT_ATTACHMENT, c.getId()));
    }

    private void validateDates(ContractRequest req) {
        if (req.getEndDate().isBefore(req.getStartDate())) {
            throw new BusinessRuleException("The end date can't be before the start date");
        }
        // The rest only constrains a contract that actually has a schedule.
        if (req.getContractType() != null && !req.getContractType().hasSchedule()) return;

        LocalDate first = req.getFirstVisitDate() == null ? req.getStartDate() : req.getFirstVisitDate();
        if (first.isBefore(req.getStartDate()) || first.isAfter(req.getEndDate())) {
            throw new BusinessRuleException("The first scheduled date must fall inside the contract term");
        }
        if (req.getTicketLeadDays() > cfg.getMaxTicketLeadDays()) {
            throw new BusinessRuleException("Tickets can be raised at most " + cfg.getMaxTicketLeadDays() + " days ahead of a visit");
        }
    }

    private void apply(ServiceContract c, ContractRequest req) {
        Company company = companies.findById(req.getCompanyId())
            .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + req.getCompanyId()));

        c.setCompany(company);
        c.setName(req.getName().trim());
        c.setContractNumber(blankToNull(req.getContractNumber()));
        c.setDescription(blankToNull(req.getDescription()));
        c.setStatus(req.getStatus());
        c.setContractType(req.getContractType());
        c.setStartDate(req.getStartDate());
        c.setEndDate(req.getEndDate());
        c.setCoveredAssets(blankToNull(req.getCoveredAssets()));
        c.setOwner(resolveOwner(req.getOwnerId()));
        c.setRenewalReminderDays(req.getRenewalReminderDays() == null || req.getRenewalReminderDays() <= 0
            ? null : req.getRenewalReminderDays());

        if (c.hasSchedule()) {
            c.setFrequency(req.getFrequency());
            c.setOccurrences(req.getFrequency() == ContractFrequency.ONE_OFF ? 1 : req.getOccurrences());
            c.setFirstVisitDate(req.getFirstVisitDate() == null ? req.getStartDate() : req.getFirstVisitDate());
            c.setAutoCreateTicket(req.isAutoCreateTicket());
            c.setTicketLeadDays(req.getTicketLeadDays());
            c.setTicketPriority(req.getTicketPriority());
            c.setTicketCategory(req.getTicketCategory());
        } else {
            // A ticket-based contract has nothing to schedule and therefore nothing to auto-raise.
            // These are written to safe values rather than left alone so that switching a contract
            // to TICKET_BASED can't leave an armed auto-ticket setting behind, and the columns stay
            // non-null for the ones the schema requires.
            c.setFrequency(ContractFrequency.ONE_OFF);
            c.setOccurrences(1);
            c.setFirstVisitDate(req.getStartDate());
            c.setAutoCreateTicket(false);
            c.setTicketLeadDays(0);
            c.setTicketPriority(req.getTicketPriority());
            c.setTicketCategory(req.getTicketCategory());
        }

        // Extending the term re-arms the renewal reminder; the sweep compares against this.
        if (c.getRenewalReminderSentFor() != null && !c.getRenewalReminderSentFor().equals(c.getEndDate())) {
            c.setRenewalReminderSentFor(null);
        }
    }

    /** The renewal owner has to be provider staff — a customer can't be told to renew their own contract. */
    private User resolveOwner(Long ownerId) {
        if (ownerId == null) return null;
        User owner = users.findById(ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + ownerId));
        if (owner.getRole() != Roles.ADMIN && owner.getRole() != Roles.ENGINEER) {
            throw new BusinessRuleException("A contract owner must be an admin or engineer");
        }
        return owner;
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
