package com.konohahelpdesk.dto.subscription;

import com.konohahelpdesk.model.plan.PlanType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Subscription selection for a company.
 *
 * PREDEFINED: only planId is used; features/limits/sla are ignored (inherited, read-only).
 * CUSTOM:     planId is ignored; featureCodes/limits/sla are required.
 */
public class CompanySubscriptionRequest {

    @NotNull
    private PlanType subscriptionType;

    private Long planId;

    /** CUSTOM: codes to ENABLE. Everything else in the catalog is disabled. */
    private List<String> featureCodes = new ArrayList<>();

    @Valid
    private LimitsDto limits;

    @Valid
    private List<SlaDto> sla = new ArrayList<>();

    public PlanType getSubscriptionType() { return subscriptionType; }
    public void setSubscriptionType(PlanType subscriptionType) { this.subscriptionType = subscriptionType; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public List<String> getFeatureCodes() { return featureCodes; }
    public void setFeatureCodes(List<String> featureCodes) { this.featureCodes = featureCodes; }
    public LimitsDto getLimits() { return limits; }
    public void setLimits(LimitsDto limits) { this.limits = limits; }
    public List<SlaDto> getSla() { return sla; }
    public void setSla(List<SlaDto> sla) { this.sla = sla; }
}
