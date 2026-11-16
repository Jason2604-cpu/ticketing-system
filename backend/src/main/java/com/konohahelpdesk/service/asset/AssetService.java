package com.konohahelpdesk.service.asset;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.dto.asset.AssetCompanyRow;
import com.konohahelpdesk.dto.asset.AssetRequest;
import com.konohahelpdesk.dto.asset.AssetResponse;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.exception.DuplicateResourceException;
import com.konohahelpdesk.exception.ResourceNotFoundException;
import com.konohahelpdesk.model.asset.Asset;
import com.konohahelpdesk.model.asset.AssetAssignment;
import com.konohahelpdesk.model.asset.AssetStatus;
import com.konohahelpdesk.model.asset.AssetType;
import com.konohahelpdesk.model.asset.WarrantyState;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.model.asset.AssetGroup;
import com.konohahelpdesk.repository.asset.AssetGroupRepository;
import com.konohahelpdesk.repository.asset.AssetRepository;
import com.konohahelpdesk.repository.contract.ServiceContractRepository;
import com.konohahelpdesk.repository.company.CompanyRepository;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
import com.konohahelpdesk.service.company.CompanyScope;
import com.konohahelpdesk.service.subscription.CompanySubscriptionService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The customer asset register.
 *
 * Authorization lives HERE, not only on the controller, because customers legitimately read from
 * these endpoints — so unlike {@code ContractController} there is no single class-level
 * {@code @PreAuthorize} doing the work. Every read resolves through {@link CompanyScope} and, for
 * a customer, through the ASSET_MANAGEMENT plan feature as well.
 */
@Service
public class AssetService {

    /** Plan feature that gates the customer-facing side. Staff are never plan-gated. */
    public static final String FEATURE = "ASSET_MANAGEMENT";

    private static final List<AssetStatus> OUT_OF_SERVICE = List.of(AssetStatus.RETIRED, AssetStatus.DISPOSED);

    private final AssetRepository assets;
    private final AssetGroupRepository groups;
    private final ServiceContractRepository contracts;
    private final CompanyRepository companies;
    private final UserRepository users;
    private final CompanyScope scope;
    private final CompanySubscriptionService subscriptions;
    private final AuditLogger audit;
    private final AppProperties.Assets cfg;

    public AssetService(AssetRepository assets, AssetGroupRepository groups, ServiceContractRepository contracts,
                        CompanyRepository companies, UserRepository users,
                        CompanyScope scope, CompanySubscriptionService subscriptions,
                        AuditLogger audit, AppProperties props) {
        this.assets = assets;
        this.groups = groups;
        this.contracts = contracts;
        this.companies = companies;
        this.users = users;
        this.scope = scope;
        this.subscriptions = subscriptions;
        this.audit = audit;
        this.cfg = props.getAssets();
    }

    // ── The company picker ───────────────────────────────────────────────────

    /**
     * Companies the actor may work in, each with its asset and warranty counts.
     *
     * The counts come from ONE grouped query for the whole page. Doing it per row would repeat the
     * mistake {@code CompanyService.list()} already carries, on a screen the UI polls every 15s.
     */
    @Transactional(readOnly = true)
    public List<AssetCompanyRow> companiesWithCounts(User actor) {
        List<Company> visible = scope.visibleCompanies(actor);
        if (visible.isEmpty()) return List.of();

        List<Long> ids = visible.stream().map(Company::getId).toList();
        LocalDate today = LocalDate.now();
        Map<Long, long[]> counts = new HashMap<>();
        for (Object[] row : assets.countsByCompany(ids, today, today.plusDays(cfg.getExpiringSoonDays()), OUT_OF_SERVICE)) {
            counts.put(num(row[0]), new long[] { num(row[1]), num(row[2]), num(row[3]) });
        }

        List<AssetCompanyRow> out = new ArrayList<>(visible.size());
        for (Company c : visible) {
            long[] n = counts.getOrDefault(c.getId(), new long[] { 0, 0, 0 });
            out.add(new AssetCompanyRow(
                c.getId(),
                c.getName(),
                c.getLogoId() == null ? null : "/api/media/" + c.getLogoId(),
                n[0], n[1], n[2]));
        }
        return out;
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    /**
     * The list for one company (or every visible company when {@code companyId} is null).
     *
     * Note which filters run where. Everything backed by a column — including the warranty-end
     * DATE RANGE — goes into the Specification and is filtered by Postgres. {@code warrantyState}
     * cannot: it is arithmetic against today, there is no column to index, so it is applied in
     * memory afterwards. Conflating the two is how you end up writing a query that can't be written.
     */
    @Transactional(readOnly = true)
    public List<AssetResponse> list(User actor, Long companyId, List<AssetType> types, List<AssetStatus> statuses,
                                    AssetAssignment assignedTo, Long ownerId, List<Long> groupIds, boolean ungrouped,
                                    WarrantyState warrantyState,
                                    LocalDate warrantyEndFrom, LocalDate warrantyEndTo, String q) {
        List<Long> companyIds = resolveReadScope(actor, companyId);
        if (companyIds != null && companyIds.isEmpty()) return List.of();

        final String needle = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";

        Specification<Asset> spec = (root, cq, cb) -> {
            if (cq != null && cq.getResultType() == Asset.class) {
                root.fetch("company", jakarta.persistence.criteria.JoinType.LEFT);
                root.fetch("owner", jakarta.persistence.criteria.JoinType.LEFT);
                root.fetch("group", jakarta.persistence.criteria.JoinType.LEFT);
                // No DISTINCT: only to-one associations are fetched, so rows can't repeat, and
                // SELECT DISTINCT over the text columns (notes) is pure sorting cost.
            }
            List<Predicate> p = new ArrayList<>();
            if (companyIds != null) p.add(root.get("company").get("id").in(companyIds));
            if (types != null && !types.isEmpty()) p.add(root.get("type").in(types));
            if (statuses != null && !statuses.isEmpty()) p.add(root.get("status").in(statuses));
            if (assignedTo != null) p.add(cb.equal(root.get("assignedTo"), assignedTo));
            if (ownerId != null) p.add(cb.equal(root.get("owner").get("id"), ownerId));
            // Group filter: any of the listed groups, OR no group at all when "ungrouped" is ticked.
            boolean byGroup = groupIds != null && !groupIds.isEmpty();
            if (byGroup || ungrouped) {
                List<Predicate> any = new ArrayList<>();
                // An explicit LEFT join: an implicit path join is INNER and would drop ungrouped rows
                // before the OR ever sees them.
                if (byGroup) any.add(root.join("group", jakarta.persistence.criteria.JoinType.LEFT).get("id").in(groupIds));
                if (ungrouped) any.add(cb.isNull(root.get("group")));
                p.add(cb.or(any.toArray(new Predicate[0])));
            }
            if (warrantyEndFrom != null) p.add(cb.greaterThanOrEqualTo(root.get("warrantyEnd"), warrantyEndFrom));
            if (warrantyEndTo != null) p.add(cb.lessThanOrEqualTo(root.get("warrantyEnd"), warrantyEndTo));
            if (needle != null) {
                p.add(cb.or(
                    cb.like(cb.lower(root.get("name")), needle),
                    cb.like(cb.lower(cb.coalesce(root.get("assetTag"), "")), needle),
                    cb.like(cb.lower(cb.coalesce(root.get("serialNumber"), "")), needle),
                    cb.like(cb.lower(cb.coalesce(root.get("model"), "")), needle),
                    cb.like(cb.lower(cb.coalesce(root.get("make"), "")), needle),
                    cb.like(cb.lower(cb.coalesce(root.get("location"), "")), needle)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };

        LocalDate today = LocalDate.now();
        return assets.findAll(spec).stream()
            .filter(a -> warrantyState == null || a.warrantyState(today, cfg.getExpiringSoonDays()) == warrantyState)
            .sorted((x, y) -> String.CASE_INSENSITIVE_ORDER.compare(
                x.getName() == null ? "" : x.getName(), y.getName() == null ? "" : y.getName()))
            .map(a -> AssetResponse.of(a, today, cfg.getExpiringSoonDays()))
            .toList();
    }

    @Transactional(readOnly = true)
    public AssetResponse get(User actor, Long id) {
        Asset a = load(id);
        requireReadAccess(actor, a.getCompany().getId());
        return respond(a);
    }

    /** For the media access rule in phase 5, and anywhere else that holds only an id. */
    public boolean canRead(User actor, Long assetId) {
        return assets.findDetailById(assetId)
            .map(a -> {
                try {
                    requireReadAccess(actor, a.getCompany().getId());
                    return true;
                } catch (RuntimeException denied) {
                    return false;
                }
            })
            .orElse(false);
    }

    // ── Write ────────────────────────────────────────────────────────────────

    @Transactional
    public AssetResponse create(AssetRequest req, User actor) {
        requireWriteAccess(actor, req.getCompanyId());
        Asset a = new Asset();
        apply(a, req, null);
        Asset saved = assets.save(a);
        audit.event("ASSET_CREATED", "assetId=" + saved.getId()
            + " companyId=" + saved.getCompany().getId()
            + " type=" + saved.getType()
            + " assignedTo=" + saved.getAssignedTo());
        return respond(load(saved.getId()));
    }

    @Transactional
    public AssetResponse update(Long id, AssetRequest req, User actor) {
        Asset a = load(id);
        // Both ends: you must be allowed to touch the company it is in now, AND the one the
        // request is moving it to. Checking only the target would let someone move an asset out
        // of a company they can't see; checking only the source would let them move it into one.
        requireWriteAccess(actor, a.getCompany().getId());
        requireWriteAccess(actor, req.getCompanyId());
        if (!a.getCompany().getId().equals(req.getCompanyId()) && contracts.countCoveringAsset(id) > 0) {
            // A contract only ever holds its own company's kit. (The group is re-validated in apply().)
            throw new BusinessRuleException("This asset is listed on a service contract, so it can't move to another company — remove it from the contract first");
        }
        apply(a, req, id);
        assets.save(a);
        audit.event("ASSET_UPDATED", "assetId=" + id + " companyId=" + req.getCompanyId());
        return respond(load(id));
    }

    /**
     * Hard delete, refused once anything references the asset.
     *
     * RETIRED is the answer for kit that has left the building: losing an asset off a two-year-old
     * ticket, so that nobody can tell what the visit was about, is worse than carrying a row that
     * says "retired". The reference checks land in phases 6 and 7 as the ticket and contract links
     * are added — the guard is written here so the rule exists before there is anything to break it.
     */
    @Transactional
    public void delete(Long id, User actor) {
        Asset a = load(id);
        requireWriteAccess(actor, a.getCompany().getId());
        long covering = contracts.countCoveringAsset(id);
        if (covering > 0) {
            throw new BusinessRuleException("This asset is listed on " + covering + " service contract"
                + (covering == 1 ? "" : "s") + " — remove it there first, or mark it Retired instead");
        }
        assets.delete(a);
        audit.event("ASSET_DELETED", "assetId=" + id + " companyId=" + a.getCompany().getId());
    }

    // ── Mapping + invariants ─────────────────────────────────────────────────

    /**
     * Maps a request onto an asset and enforces the assignment invariants.
     *
     * THE CLEARING IS THE POINT. Each mode writes the fields it owns and NULLS the other two, so
     * switching SYSTEM_USER -> GUEST -> SHARED can never leave a stale owner id or a guest named
     * after somebody who now has a login. Same defensive shape as
     * {@code ContractService.apply()} zeroing the schedule fields on a ticket-based contract.
     */
    private void apply(Asset a, AssetRequest req, Long existingId) {
        Company company = companies.findById(req.getCompanyId())
            .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + req.getCompanyId()));

        String tag = blankToNull(req.getAssetTag());
        if (tag != null) {
            boolean clash = existingId == null
                ? assets.existsByCompanyIdAndAssetTagIgnoreCase(company.getId(), tag)
                : assets.existsByCompanyIdAndAssetTagIgnoreCaseAndIdNot(company.getId(), tag, existingId);
            if (clash) {
                throw new DuplicateResourceException("Another asset at this company already uses the tag " + tag);
            }
        }

        if (req.getWarrantyStart() != null && req.getWarrantyEnd() != null
            && req.getWarrantyEnd().isBefore(req.getWarrantyStart())) {
            throw new BusinessRuleException("The warranty end date can't be before the warranty start date");
        }

        a.setCompany(company);
        a.setName(req.getName().trim());
        a.setAssetTag(tag);
        a.setType(req.getType());
        a.setMake(blankToNull(req.getMake()));
        a.setModel(blankToNull(req.getModel()));
        // A duplicate serial within a company is suspicious but legitimate often enough (two
        // identical spares, a vendor that reuses a batch number) that it must not block a save.
        // The CSV preview in phase 4 is where it surfaces as a warning, because that is the first
        // place in this module with somewhere to put one.
        a.setSerialNumber(blankToNull(req.getSerialNumber()));
        a.setLocation(blankToNull(req.getLocation()));
        a.setGroup(resolveGroup(req.getGroupId(), company.getId()));
        a.setStatus(req.getStatus());

        applyAssignment(a, req, company.getId());

        a.setPurchaseDate(req.getPurchaseDate());
        a.setWarrantyStart(req.getWarrantyStart());
        a.setWarrantyEnd(req.getWarrantyEnd());
        a.setNotes(blankToNull(req.getNotes()));

        applyReminder(a, req);
    }

    private void applyAssignment(Asset a, AssetRequest req, Long companyId) {
        AssetAssignment mode = req.getAssignedTo() == null ? AssetAssignment.SHARED : req.getAssignedTo();
        a.setAssignedTo(mode);

        switch (mode) {
            case SYSTEM_USER -> {
                if (req.getOwnerId() == null) {
                    throw new BusinessRuleException("Pick the user this asset is assigned to");
                }
                a.setOwner(resolveOwner(req.getOwnerId(), companyId));
                a.setGuestName(null);
                a.setGuestEmail(null);
            }
            case GUEST -> {
                String guest = blankToNull(req.getGuestName());
                if (guest == null) {
                    throw new BusinessRuleException("Enter the name of the person this asset is assigned to");
                }
                a.setGuestName(guest);
                a.setGuestEmail(blankToNull(req.getGuestEmail()));
                a.setOwner(null);
            }
            case SHARED -> {
                a.setOwner(null);
                a.setGuestName(null);
                a.setGuestEmail(null);
            }
        }
    }

    private void applyReminder(Asset a, AssetRequest req) {
        if (!req.isWarrantyReminderEnabled()) {
            a.setWarrantyReminderEnabled(false);
            a.setWarrantyReminderDays(null);
            return;
        }
        if (a.getWarrantyEnd() == null) {
            throw new BusinessRuleException("Set a warranty end date before turning the reminder on");
        }
        a.setWarrantyReminderEnabled(true);
        a.setWarrantyReminderDays(req.getWarrantyReminderDays() == null
            ? cfg.getDefaultReminderDays() : req.getWarrantyReminderDays());

        // Extending the warranty re-arms the reminder — the sweep compares the marker against the
        // current end date, so clearing it here is what makes a renewed warranty notify again.
        if (a.getWarrantyReminderSentFor() != null && !a.getWarrantyReminderSentFor().equals(a.getWarrantyEnd())) {
            a.setWarrantyReminderSentFor(null);
        }
    }

    /**
     * The owner must be a live user OF THIS COMPANY. Without the company check an admin could
     * assign one customer's laptop to another customer's employee, and that name would then show
     * on a page the first customer can read.
     */
    private User resolveOwner(Long ownerId, Long companyId) {
        User owner = users.findById(ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + ownerId));
        if (owner.getStatus() == UserStatus.IS_DELETED) {
            throw new BusinessRuleException("That user account has been removed");
        }
        Long ownerCompany = owner.getCompany() == null ? null : owner.getCompany().getId();
        if (ownerCompany == null || !ownerCompany.equals(companyId)) {
            throw new BusinessRuleException("That user doesn't belong to this company");
        }
        return owner;
    }

    // ── Access ───────────────────────────────────────────────────────────────

    /**
     * Read scope as a company-id list, or null for "no restriction".
     *
     * A customer additionally has to be on a plan that includes assets — and gets a 403 from
     * requireFeature, not a quietly empty list, because an empty register and an unpaid feature
     * look identical in the UI and only one of them is worth telling them about.
     */
    private List<Long> resolveReadScope(User actor, Long companyId) {
        List<Long> visible = scope.visibleCompanyIds(actor);

        if (companyId != null) {
            if (visible != null && !visible.contains(companyId)) {
                throw new org.springframework.security.access.AccessDeniedException("You do not have access to that company");
            }
            requireFeatureIfCustomer(actor, companyId);
            return List.of(companyId);
        }

        if (visible != null && isCustomer(actor)) {
            visible.forEach(id -> requireFeatureIfCustomer(actor, id));
        }
        return visible;
    }

    void requireReadAccess(User actor, Long companyId) {
        scope.requireAccess(actor, companyId);
        requireFeatureIfCustomer(actor, companyId);
    }

    /**
     * Writes are staff-only. The controller already gates the role/privilege side; this adds the
     * company scoping, so an engineer assigned to two customers can't register kit for a third.
     */
    void requireWriteAccess(User actor, Long companyId) {
        if (companyId == null) throw new BusinessRuleException("Company is required");
        if (isCustomer(actor)) {
            throw new org.springframework.security.access.AccessDeniedException("Assets are maintained by your support team");
        }
        scope.requireAccess(actor, companyId);
    }

    private void requireFeatureIfCustomer(User actor, Long companyId) {
        if (isCustomer(actor)) subscriptions.requireFeature(companyId, FEATURE);
    }

    private boolean isCustomer(User actor) {
        // Not "anything but ADMIN/ENGINEER": a Default employee with Manage Assets is staff, and
        // their company reach comes from CompanyScope, not from a plan feature.
        return actor.getRole().isCustomerSide();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Asset load(Long id) {
        return assets.findDetailById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Asset not found: " + id));
    }

    /** The group must exist and belong to the asset's company — no cross-customer groups. */
    private AssetGroup resolveGroup(Long groupId, Long companyId) {
        if (groupId == null) return null;
        AssetGroup g = groups.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Asset group not found: " + groupId));
        if (!g.getCompany().getId().equals(companyId)) {
            throw new BusinessRuleException("That asset group belongs to a different company");
        }
        return g;
    }

    private AssetResponse respond(Asset a) {
        return AssetResponse.of(a, LocalDate.now(), cfg.getExpiringSoonDays());
    }

    private static long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
