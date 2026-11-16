package com.konohahelpdesk.service.asset;

import com.konohahelpdesk.dto.asset.AssetGroupRequest;
import com.konohahelpdesk.dto.asset.AssetGroupResponse;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.exception.DuplicateResourceException;
import com.konohahelpdesk.exception.ResourceNotFoundException;
import com.konohahelpdesk.model.asset.AssetGroup;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.repository.asset.AssetGroupRepository;
import com.konohahelpdesk.repository.asset.AssetRepository;
import com.konohahelpdesk.repository.company.CompanyRepository;
import com.konohahelpdesk.repository.contract.ServiceContractRepository;
import com.konohahelpdesk.security.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Asset groups — named sets of one company's assets that a service contract can cover as a unit.
 *
 * Access follows the asset register exactly, by delegating to {@link AssetService}'s checks:
 * reads need company scope (plus the ASSET_MANAGEMENT plan feature for a customer), writes are
 * staff-only and company-scoped.
 */
@Service
public class AssetGroupService {

    private final AssetGroupRepository groups;
    private final AssetRepository assets;
    private final CompanyRepository companies;
    private final ServiceContractRepository contracts;
    private final AssetService assetService;
    private final AuditLogger audit;

    public AssetGroupService(AssetGroupRepository groups, AssetRepository assets, CompanyRepository companies,
                             ServiceContractRepository contracts, AssetService assetService, AuditLogger audit) {
        this.groups = groups;
        this.assets = assets;
        this.companies = companies;
        this.contracts = contracts;
        this.assetService = assetService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AssetGroupResponse> list(User actor, Long companyId) {
        if (companyId == null) throw new BusinessRuleException("Company is required");
        assetService.requireReadAccess(actor, companyId);
        return groups.findForCompany(companyId).stream().map(AssetGroupResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public AssetGroupResponse get(User actor, Long id) {
        AssetGroup g = load(id);
        assetService.requireReadAccess(actor, g.getCompany().getId());
        return AssetGroupResponse.of(g);
    }

    @Transactional
    public AssetGroupResponse create(AssetGroupRequest req, User actor) {
        assetService.requireWriteAccess(actor, req.getCompanyId());
        AssetGroup g = new AssetGroup();
        apply(g, req, null);
        AssetGroup saved = groups.save(g);
        audit.event("ASSET_GROUP_CREATED", "groupId=" + saved.getId() + " companyId=" + req.getCompanyId());
        return AssetGroupResponse.of(load(saved.getId()));
    }

    @Transactional
    public AssetGroupResponse update(Long id, AssetGroupRequest req, User actor) {
        AssetGroup g = load(id);
        assetService.requireWriteAccess(actor, g.getCompany().getId());
        assetService.requireWriteAccess(actor, req.getCompanyId());
        if (!g.getCompany().getId().equals(req.getCompanyId())
            && (contracts.countCoveringGroup(id) > 0 || assets.countByGroupId(id) > 0)) {
            throw new BusinessRuleException("This group has assets or is covered by a service contract, so it can't move to another company");
        }
        apply(g, req, id);
        groups.save(g);
        audit.event("ASSET_GROUP_UPDATED", "groupId=" + id);
        return AssetGroupResponse.of(load(id));
    }

    /**
     * Refused while a contract covers the group: deleting it would silently shrink that contract's
     * coverage. Remove it from the contract first. Its assets are kept and simply become ungrouped.
     */
    @Transactional
    public void delete(Long id, User actor) {
        AssetGroup g = load(id);
        assetService.requireWriteAccess(actor, g.getCompany().getId());
        long covering = contracts.countCoveringGroup(id);
        if (covering > 0) {
            throw new BusinessRuleException("This group is covered by " + covering + " service contract"
                + (covering == 1 ? "" : "s") + " — remove it from " + (covering == 1 ? "that contract" : "those contracts")
                + " before deleting it");
        }
        Long companyId = g.getCompany().getId();
        int ungrouped = assets.clearGroup(id);
        groups.deleteById(id);
        audit.event("ASSET_GROUP_DELETED", "ungrouped=" + ungrouped + " groupId=" + id + " companyId=" + companyId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void apply(AssetGroup g, AssetGroupRequest req, Long existingId) {
        Company company = companies.findById(req.getCompanyId())
            .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + req.getCompanyId()));

        String name = req.getName().trim();
        boolean clash = existingId == null
            ? groups.existsByCompanyIdAndNameIgnoreCase(company.getId(), name)
            : groups.existsByCompanyIdAndNameIgnoreCaseAndIdNot(company.getId(), name, existingId);
        if (clash) throw new DuplicateResourceException("This company already has a group called " + name);

        g.setCompany(company);
        g.setName(name);
        String d = req.getDescription() == null ? null : req.getDescription().trim();
        g.setDescription(d == null || d.isEmpty() ? null : d);
    }

    private AssetGroup load(Long id) {
        return groups.findDetailById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Asset group not found: " + id));
    }
}
