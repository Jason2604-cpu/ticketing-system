package com.konohahelpdesk.service.subscription;

import com.konohahelpdesk.dto.subscription.FeatureResponse;
import com.konohahelpdesk.dto.subscription.LimitsDto;
import com.konohahelpdesk.dto.subscription.PlanRequest;
import com.konohahelpdesk.dto.subscription.PlanResponse;
import com.konohahelpdesk.dto.subscription.SlaDto;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.exception.DuplicateResourceException;
import com.konohahelpdesk.exception.ResourceNotFoundException;
import com.konohahelpdesk.model.plan.Feature;
import com.konohahelpdesk.model.plan.Plan;
import com.konohahelpdesk.model.plan.PlanFeature;
import com.konohahelpdesk.model.plan.PlanSlaConfiguration;
import com.konohahelpdesk.model.plan.PlanStatus;
import com.konohahelpdesk.model.plan.PlanType;
import com.konohahelpdesk.model.plan.SlaPriority;
import com.konohahelpdesk.repository.company.CompanySubscriptionRepository;
import com.konohahelpdesk.repository.plan.PlanFeatureRepository;
import com.konohahelpdesk.repository.plan.PlanRepository;
import com.konohahelpdesk.repository.plan.PlanSlaConfigurationRepository;
import com.konohahelpdesk.security.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Admin CRUD for predefined plans (Subscription Management → Plans). */
@Service
public class PlanService {

    private final PlanRepository planRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final PlanSlaConfigurationRepository planSlaRepository;
    private final CompanySubscriptionRepository subscriptionRepository;
    private final FeatureService featureService;
    private final SubscriptionValidator validator;
    private final AuditLogger audit;

    public PlanService(PlanRepository planRepository,
                       PlanFeatureRepository planFeatureRepository,
                       PlanSlaConfigurationRepository planSlaRepository,
                       CompanySubscriptionRepository subscriptionRepository,
                       FeatureService featureService,
                       SubscriptionValidator validator,
                       AuditLogger audit) {
        this.planRepository = planRepository;
        this.planFeatureRepository = planFeatureRepository;
        this.planSlaRepository = planSlaRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.featureService = featureService;
        this.validator = validator;
        this.audit = audit;
    }

    // ── queries ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PlanResponse> list(boolean includeInactive) {
        List<Plan> plans = includeInactive ? planRepository.findAll() : planRepository.findByStatus(PlanStatus.ACTIVE);
        return plans.stream()
            .sorted(Comparator.comparing(Plan::getId))
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse get(Long id) {
        return toResponse(load(id));
    }

    public Plan load(Long id) {
        return planRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + id));
    }

    // ── commands ─────────────────────────────────────────────────────────────

    @Transactional
    public PlanResponse create(PlanRequest req) {
        String name = req.getPlanName().trim();
        if (planRepository.existsByPlanNameIgnoreCase(name)) {
            throw new DuplicateResourceException("A plan named '" + name + "' already exists");
        }
        Plan plan = new Plan();
        plan.setPlanType(PlanType.PREDEFINED);
        apply(plan, req, name);
        Plan saved = planRepository.save(plan);
        replaceFeatures(saved, req.getFeatureCodes());
        replaceSla(saved, req.getSla());
        audit.event("PLAN_CREATED", "planId=" + saved.getId() + " name=" + name);
        return toResponse(saved);
    }

    @Transactional
    public PlanResponse update(Long id, PlanRequest req) {
        Plan plan = load(id);
        String name = req.getPlanName().trim();
        if (planRepository.existsByPlanNameIgnoreCaseAndIdNot(name, id)) {
            throw new DuplicateResourceException("A plan named '" + name + "' already exists");
        }
        apply(plan, req, name);
        Plan saved = planRepository.save(plan);
        replaceFeatures(saved, req.getFeatureCodes());
        replaceSla(saved, req.getSla());
        audit.event("PLAN_UPDATED", "planId=" + id);
        return toResponse(saved);
    }

    @Transactional
    public PlanResponse setStatus(Long id, PlanStatus status) {
        Plan plan = load(id);
        plan.setStatus(status);
        audit.event("PLAN_STATUS_CHANGED", "planId=" + id + " status=" + status);
        return toResponse(planRepository.save(plan));
    }

    /** Hard delete is only allowed when no company is on the plan; otherwise deactivate it. */
    @Transactional
    public void delete(Long id) {
        Plan plan = load(id);
        if (subscriptionRepository.existsByPlanId(id)) {
            throw new BusinessRuleException("Plan is in use by one or more companies; set it INACTIVE instead");
        }
        planFeatureRepository.deleteByPlanId(id);
        planSlaRepository.deleteByPlanId(id);
        planRepository.delete(plan);
        audit.event("PLAN_DELETED", "planId=" + id);
    }

    // ── internals ────────────────────────────────────────────────────────────

    private void apply(Plan plan, PlanRequest req, String name) {
        validator.validateLimits(req.getLimits());
        plan.setPlanName(name);
        plan.setDescription(req.getDescription() == null ? null : req.getDescription().trim());
        plan.setStatus(req.getStatus() == null ? PlanStatus.ACTIVE : req.getStatus());
        plan.setMaxUsers(req.getLimits().getMaxUsers());
        plan.setMonthlyTicketLimit(req.getLimits().getMonthlyTicketLimit());
        plan.setStorageLimitMb(req.getLimits().getStorageLimitMb());
    }

    void replaceFeatures(Plan plan, List<String> featureCodes) {
        List<Feature> enabled = featureService.resolveCodes(featureCodes);
        Set<Long> enabledIds = enabled.stream().map(Feature::getId).collect(Collectors.toSet());

        planFeatureRepository.deleteByPlanId(plan.getId());
        planFeatureRepository.flush();
        for (Feature f : featureService.allFeatures()) {
            PlanFeature pf = new PlanFeature();
            pf.setPlan(plan);
            pf.setFeature(f);
            pf.setEnabled(enabledIds.contains(f.getId()));
            planFeatureRepository.save(pf);
        }
    }

    void replaceSla(Plan plan, List<SlaDto> sla) {
        Map<SlaPriority, SlaDto> complete = validator.validateAndComplete(sla);
        planSlaRepository.deleteByPlanId(plan.getId());
        planSlaRepository.flush();
        for (SlaDto row : complete.values()) {
            PlanSlaConfiguration c = new PlanSlaConfiguration();
            c.setPlan(plan);
            c.setPriority(row.getPriority());
            c.setResponseTimeMinutes(row.getResponseTimeMinutes());
            c.setResolutionTimeMinutes(row.getResolutionTimeMinutes());
            planSlaRepository.save(c);
        }
    }

    public PlanResponse toResponse(Plan plan) {
        PlanResponse r = new PlanResponse();
        r.setId(plan.getId());
        r.setPlanName(plan.getPlanName());
        r.setDescription(plan.getDescription());
        r.setPlanType(plan.getPlanType());
        r.setStatus(plan.getStatus());
        r.setFeatures(featuresOf(plan));
        r.setLimits(limitsOf(plan));
        r.setSla(slaOf(plan));
        r.setCreatedAt(plan.getCreatedAt());
        r.setUpdatedAt(plan.getUpdatedAt());
        return r;
    }

    public List<FeatureResponse> featuresOf(Plan plan) {
        Map<Long, Boolean> enabledById = planFeatureRepository.findByPlanId(plan.getId()).stream()
            .collect(Collectors.toMap(pf -> pf.getFeature().getId(), PlanFeature::isEnabled));
        return featureService.allFeatures().stream()
            .map(f -> FeatureResponse.of(f, enabledById.getOrDefault(f.getId(), false)))
            .toList();
    }

    public LimitsDto limitsOf(Plan plan) {
        return new LimitsDto(plan.getMaxUsers(), plan.getMonthlyTicketLimit(), plan.getStorageLimitMb());
    }

    public List<SlaDto> slaOf(Plan plan) {
        return planSlaRepository.findByPlanId(plan.getId()).stream()
            .sorted(Comparator.comparing(PlanSlaConfiguration::getPriority))
            .map(c -> new SlaDto(c.getPriority(), c.getResponseTimeMinutes(), c.getResolutionTimeMinutes()))
            .toList();
    }
}
