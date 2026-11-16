package com.konohahelpdesk.service.subscription;

import com.konohahelpdesk.model.user.User;

import com.konohahelpdesk.dto.subscription.CompanySubscriptionRequest;
import com.konohahelpdesk.dto.subscription.CompanySubscriptionResponse;
import com.konohahelpdesk.dto.subscription.FeatureResponse;
import com.konohahelpdesk.dto.subscription.LimitsDto;
import com.konohahelpdesk.dto.subscription.SlaDto;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.exception.LimitExceededException;
import com.konohahelpdesk.exception.ResourceNotFoundException;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.company.CompanyLimitConfiguration;
import com.konohahelpdesk.model.company.CompanySlaConfiguration;
import com.konohahelpdesk.model.company.CompanySubscription;
import com.konohahelpdesk.model.company.CompanySubscriptionFeatureOverride;
import com.konohahelpdesk.model.plan.Feature;
import com.konohahelpdesk.model.plan.Plan;
import com.konohahelpdesk.model.plan.PlanStatus;
import com.konohahelpdesk.model.plan.PlanType;
import com.konohahelpdesk.model.plan.SlaPriority;
import com.konohahelpdesk.model.plan.SubscriptionStatus;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.repository.company.CompanyLimitConfigurationRepository;
import com.konohahelpdesk.repository.ticket.TicketRepository;
import com.konohahelpdesk.service.media.MediaStorageService;
import com.konohahelpdesk.repository.company.CompanyRepository;
import com.konohahelpdesk.repository.company.CompanySlaConfigurationRepository;
import com.konohahelpdesk.repository.company.CompanySubscriptionFeatureOverrideRepository;
import com.konohahelpdesk.repository.company.CompanySubscriptionRepository;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The single source of truth for "what is this company allowed to do".
 *
 * Resolution rules (requirement §2, §5, §8):
 *  - PREDEFINED: features, limits and SLA are read live from the plan (locked on the company).
 *  - CUSTOM:     features from company_subscription_feature_overrides, limits from
 *                company_limit_configurations, SLA from company_sla_configurations.
 *  - Exactly one subscription row per company (DB unique constraint + service check).
 *
 * Other modules must call {@link #hasFeature(Long, String)} / {@link #requireFeature(Long, String)}
 * rather than checking plan names — no hard-coded plan checks anywhere.
 */
@Service
public class CompanySubscriptionService {

    private final CompanyRepository companyRepository;
    private final CompanySubscriptionRepository subscriptionRepository;
    private final CompanySubscriptionFeatureOverrideRepository overrideRepository;
    private final CompanyLimitConfigurationRepository limitRepository;
    private final CompanySlaConfigurationRepository slaRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final MediaStorageService mediaStorage;
    private final PlanService planService;
    private final FeatureService featureService;
    private final SubscriptionValidator validator;
    private final AuditLogger audit;

    public CompanySubscriptionService(CompanyRepository companyRepository,
                                      CompanySubscriptionRepository subscriptionRepository,
                                      CompanySubscriptionFeatureOverrideRepository overrideRepository,
                                      CompanyLimitConfigurationRepository limitRepository,
                                      CompanySlaConfigurationRepository slaRepository,
                                      UserRepository userRepository,
                                      TicketRepository ticketRepository,
                                      MediaStorageService mediaStorage,
                                      PlanService planService,
                                      FeatureService featureService,
                                      SubscriptionValidator validator,
                                      AuditLogger audit) {
        this.companyRepository = companyRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.overrideRepository = overrideRepository;
        this.limitRepository = limitRepository;
        this.slaRepository = slaRepository;
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.mediaStorage = mediaStorage;
        this.planService = planService;
        this.featureService = featureService;
        this.validator = validator;
        this.audit = audit;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Assignment (admin)
    // ═════════════════════════════════════════════════════════════════════════

    /** Creates or replaces the company's subscription configuration. */
    @Transactional
    public CompanySubscriptionResponse assign(Long companyId, CompanySubscriptionRequest req) {
        Company company = loadCompany(companyId);
        CompanySubscription sub = subscriptionRepository.findByCompanyId(companyId).orElseGet(() -> {
            CompanySubscription s = new CompanySubscription();
            s.setCompany(company);
            return s;
        });

        if (req.getSubscriptionType() == PlanType.PREDEFINED) {
            assignPredefined(sub, req);
        } else {
            assignCustom(sub, company, req);
        }
        sub.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(sub);

        audit.event("SUBSCRIPTION_ASSIGNED", "companyId=" + companyId + " type=" + req.getSubscriptionType()
            + (sub.getPlan() != null ? " planId=" + sub.getPlan().getId() : ""));
        return getResolved(companyId);
    }

    private void assignPredefined(CompanySubscription sub, CompanySubscriptionRequest req) {
        if (req.getPlanId() == null) {
            throw new BusinessRuleException("planId is required for a PREDEFINED subscription");
        }
        Plan plan = planService.load(req.getPlanId());
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new BusinessRuleException("Plan '" + plan.getPlanName() + "' is not active");
        }
        sub.setPlan(plan);
        sub.setSubscriptionType(PlanType.PREDEFINED);
        // Predefined = inherited & locked: drop any custom rows so nothing stale is left behind.
        clearCustomRows(sub.getCompany().getId());
    }

    private void assignCustom(CompanySubscription sub, Company company, CompanySubscriptionRequest req) {
        if (req.getLimits() == null) {
            throw new BusinessRuleException("limits are required for a CUSTOM subscription");
        }
        validator.validateLimits(req.getLimits());
        Map<SlaPriority, SlaDto> sla = validator.validateAndComplete(req.getSla());
        List<Feature> enabled = featureService.resolveCodes(req.getFeatureCodes());
        Set<Long> enabledIds = enabled.stream().map(Feature::getId).collect(Collectors.toSet());

        sub.setPlan(null);
        sub.setSubscriptionType(PlanType.CUSTOM);
        Long companyId = company.getId();
        clearCustomRows(companyId);

        for (Feature f : featureService.allFeatures()) {
            CompanySubscriptionFeatureOverride o = new CompanySubscriptionFeatureOverride();
            o.setCompany(company);
            o.setFeature(f);
            o.setEnabled(enabledIds.contains(f.getId()));
            overrideRepository.save(o);
        }

        CompanyLimitConfiguration lim = new CompanyLimitConfiguration();
        lim.setCompany(company);
        lim.setMaxUsers(req.getLimits().getMaxUsers());
        lim.setMonthlyTicketLimit(req.getLimits().getMonthlyTicketLimit());
        lim.setStorageLimitMb(req.getLimits().getStorageLimitMb());
        limitRepository.save(lim);

        for (SlaDto row : sla.values()) {
            CompanySlaConfiguration c = new CompanySlaConfiguration();
            c.setCompany(company);
            c.setPriority(row.getPriority());
            c.setResponseTimeMinutes(row.getResponseTimeMinutes());
            c.setResolutionTimeMinutes(row.getResolutionTimeMinutes());
            slaRepository.save(c);
        }
    }

    private void clearCustomRows(Long companyId) {
        overrideRepository.deleteByCompanyId(companyId);
        slaRepository.deleteByCompanyId(companyId);
        limitRepository.findByCompanyId(companyId).ifPresent(limitRepository::delete);
        overrideRepository.flush();
        slaRepository.flush();
        limitRepository.flush();
    }

    @Transactional
    public CompanySubscriptionResponse setStatus(Long companyId, SubscriptionStatus status) {
        CompanySubscription sub = loadSubscription(companyId);
        sub.setStatus(status);
        subscriptionRepository.save(sub);
        audit.event("SUBSCRIPTION_STATUS_CHANGED", "companyId=" + companyId + " status=" + status);
        return getResolved(companyId);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Resolution (read)
    // ═════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public CompanySubscriptionResponse getResolved(Long companyId) {
        Company company = loadCompany(companyId);
        CompanySubscription sub = loadSubscription(companyId);

        CompanySubscriptionResponse r = new CompanySubscriptionResponse();
        r.setCompanyId(company.getId());
        r.setCompanyName(company.getName());
        r.setSubscriptionType(sub.getSubscriptionType());
        r.setStatus(sub.getStatus());
        r.setCreatedDate(sub.getCreatedDate());
        r.setUpdatedAt(sub.getUpdatedAt());

        if (sub.getSubscriptionType() == PlanType.PREDEFINED && sub.getPlan() != null) {
            Plan plan = sub.getPlan();
            r.setPlanId(plan.getId());
            r.setPlanName(plan.getPlanName());
            r.setLocked(true);
            r.setFeatures(planService.featuresOf(plan));
            r.setLimits(planService.limitsOf(plan));
            r.setSla(planService.slaOf(plan));
        } else {
            r.setLocked(false);
            r.setFeatures(customFeatures(companyId));
            r.setLimits(customLimits(companyId));
            r.setSla(customSla(companyId));
        }

        CompanySubscriptionResponse.UsageDto usage = new CompanySubscriptionResponse.UsageDto();
        usage.setActiveUsers(userRepository.countByCompanyIdAndStatusNot(companyId, UserStatus.IS_DELETED));
        usage.setStorageUsedBytes(mediaStorage.usedBytesForCompany(companyId));
        usage.setTicketsThisMonth(ticketRepository.countByCompanyIdAndCreatedAtGreaterThanEqual(companyId, monthStart()));
        r.setUsage(usage);
        return r;
    }

    /**
     * What one row of the companies list needs, and nothing more.
     *
     * @param planName    display name, or "Custom" for a bespoke subscription
     * @param maxUsers    seat limit, null when the plan sets none
     * @param activeUsers seats in use
     */
    public record SubscriptionSummary(String planName, PlanType subscriptionType, Integer maxUsers, long activeUsers) {}

    /**
     * Batch form of {@link #getResolved} for list views.
     *
     * getResolved costs roughly nine queries per company — company, subscription, plan features,
     * limits, SLA, seat count, the monthly ticket count, and the storage sum whose JPQL carries
     * three IN-subqueries. The companies list called it once per row, on a list the frontend
     * refetches every 15 seconds, so a provider with 20 customers was issuing ~180 queries every
     * 15 seconds per signed-in staff member just to render a sidebar count.
     *
     * This resolves the whole page in three queries and deliberately omits storage bytes and
     * tickets-this-month: they are only ever shown on the company detail page's Plan Usage card,
     * which still calls getResolved for exactly one company.
     */
    @Transactional(readOnly = true)
    public Map<Long, SubscriptionSummary> summariesFor(Collection<Long> companyIds) {
        if (companyIds == null || companyIds.isEmpty()) return Map.of();

        Map<Long, Long> activeUsers = new HashMap<>();
        for (Object[] row : userRepository.countActiveByCompanyIds(companyIds, UserStatus.IS_DELETED)) {
            activeUsers.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }

        // Custom limits are only read for the CUSTOM subscriptions below, but fetching them in one
        // go is cheaper than a query per custom company and costs nothing when there are none.
        Map<Long, CompanyLimitConfiguration> customLimits = new HashMap<>();
        for (CompanyLimitConfiguration l : limitRepository.findByCompanyIdIn(companyIds)) {
            customLimits.put(l.getCompany().getId(), l);
        }

        Map<Long, SubscriptionSummary> out = new HashMap<>();
        for (CompanySubscription sub : subscriptionRepository.findWithPlanByCompanyIdIn(companyIds)) {
            Long id = sub.getCompany().getId();
            long seats = activeUsers.getOrDefault(id, 0L);

            if (sub.getSubscriptionType() == PlanType.PREDEFINED && sub.getPlan() != null) {
                Plan plan = sub.getPlan();
                out.put(id, new SubscriptionSummary(plan.getPlanName(), PlanType.PREDEFINED, plan.getMaxUsers(), seats));
            } else {
                CompanyLimitConfiguration l = customLimits.get(id);
                Integer max = l != null ? l.getMaxUsers() : validator.customDefaultLimits().getMaxUsers();
                out.put(id, new SubscriptionSummary("Custom", PlanType.CUSTOM, max, seats));
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Optional<CompanySubscriptionResponse> findResolved(Long companyId) {
        return subscriptionRepository.existsByCompanyId(companyId)
            ? Optional.of(getResolved(companyId))
            : Optional.empty();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Enforcement — call these from every feature-gated / quota-bound code path
    // ═════════════════════════════════════════════════════════════════════════

    /** hasFeature(companyId, 'AUTOMATION_RULES') — requirement §8. Never throws. */
    public boolean hasFeature(Long companyId, String featureCode) {
        if (companyId == null || featureCode == null) return false;
        String code = featureCode.trim().toUpperCase(Locale.ROOT);
        // Same rule as before (ACTIVE subscription; PREDEFINED → the ACTIVE plan's features,
        // CUSTOM → the company's overrides), asked as one query instead of four.
        return subscriptionRepository.countActiveFeature(companyId, code) > 0;
    }

    /** Same as hasFeature but throws (→ HTTP 403) when the feature isn't included. */
    public void requireFeature(Long companyId, String featureCode) {
        if (!hasFeature(companyId, featureCode)) {
            throw new com.konohahelpdesk.exception.FeatureNotEnabledException(featureCode);
        }
    }

    public LimitsDto getResolvedLimits(Long companyId) {
        CompanySubscription sub = loadSubscription(companyId);
        if (sub.getSubscriptionType() == PlanType.PREDEFINED && sub.getPlan() != null) {
            return planService.limitsOf(sub.getPlan());
        }
        return customLimits(companyId);
    }

    public List<SlaDto> getResolvedSla(Long companyId) {
        CompanySubscription sub = loadSubscription(companyId);
        if (sub.getSubscriptionType() == PlanType.PREDEFINED && sub.getPlan() != null) {
            return planService.slaOf(sub.getPlan());
        }
        return customSla(companyId);
    }

    /** Throws when adding one more user would exceed max_users. */
    public void assertUserSeatAvailable(Long companyId) {
        requireActive(companyId);
        LimitsDto limits = getResolvedLimits(companyId);
        long inUse = userRepository.countByCompanyIdAndStatusNot(companyId, UserStatus.IS_DELETED);
        if (inUse >= limits.getMaxUsers()) {
            throw new LimitExceededException("User limit reached for this company (" + limits.getMaxUsers()
                + "). Upgrade the subscription to add more users.");
        }
    }

    /**
     * Ticket quota check. The ticket module owns the monthly count; pass it in.
     * (Kept here so the rule lives next to the other quota rules.)
     */
    public void assertTicketQuotaAvailable(Long companyId, long ticketsThisMonth) {
        requireActive(companyId);
        LimitsDto limits = getResolvedLimits(companyId);
        if (ticketsThisMonth >= limits.getMonthlyTicketLimit()) {
            throw new LimitExceededException("Monthly ticket limit reached for this company ("
                + limits.getMonthlyTicketLimit() + ").");
        }
    }

    /**
     * Storage quota check. Media ownership lives in the media module, so the current usage is
     * passed in (same shape as {@link #assertTicketQuotaAvailable}).
     *
     * @param usedBytes     bytes already stored for this company
     * @param incomingBytes size of the file about to be stored
     */
    public void assertStorageAvailable(Long companyId, long usedBytes, long incomingBytes) {
        requireActive(companyId);
        LimitsDto limits = getResolvedLimits(companyId);
        long limitBytes = limits.getStorageLimitMb() * 1024L * 1024L;
        if (usedBytes + incomingBytes > limitBytes) {
            long freeMb = Math.max(0, limitBytes - usedBytes) / (1024L * 1024L);
            throw new LimitExceededException("Storage limit reached for this company ("
                + limits.getStorageLimitMb() + " MB, " + freeMb + " MB free). "
                + "Delete some attachments or upgrade the subscription.");
        }
    }

    /** Start of the current calendar month — the window the monthly ticket quota is counted over. */
    private static LocalDateTime monthStart() {
        return LocalDateTime.now().with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay();
    }

    private void requireActive(Long companyId) {
        CompanySubscription sub = loadSubscription(companyId);
        if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new LimitExceededException("This company's subscription is " + sub.getStatus().name().toLowerCase(Locale.ROOT));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // internals
    // ═════════════════════════════════════════════════════════════════════════

    private List<FeatureResponse> customFeatures(Long companyId) {
        Map<Long, Boolean> enabledById = overrideRepository.findByCompanyId(companyId).stream()
            .collect(Collectors.toMap(o -> o.getFeature().getId(), CompanySubscriptionFeatureOverride::isEnabled));
        return featureService.allFeatures().stream()
            .map(f -> FeatureResponse.of(f, enabledById.getOrDefault(f.getId(), false)))
            .toList();
    }

    private LimitsDto customLimits(Long companyId) {
        return limitRepository.findByCompanyId(companyId)
            .map(l -> new LimitsDto(l.getMaxUsers(), l.getMonthlyTicketLimit(), l.getStorageLimitMb()))
            .orElseGet(validator::customDefaultLimits);
    }

    private List<SlaDto> customSla(Long companyId) {
        List<SlaDto> rows = slaRepository.findByCompanyId(companyId).stream()
            .sorted(Comparator.comparing(CompanySlaConfiguration::getPriority))
            .map(c -> new SlaDto(c.getPriority(), c.getResponseTimeMinutes(), c.getResolutionTimeMinutes()))
            .toList();
        return rows.isEmpty() ? validator.customDefaultSla() : rows;
    }

    private Company loadCompany(Long companyId) {
        return companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + companyId));
    }

    private CompanySubscription loadSubscription(Long companyId) {
        return subscriptionRepository.findByCompanyId(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("No subscription configured for company " + companyId));
    }
}
