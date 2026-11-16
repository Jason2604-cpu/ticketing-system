package com.konohahelpdesk.dto.subscription;

import com.konohahelpdesk.model.plan.Feature;

import com.konohahelpdesk.model.plan.PlanStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/** Create / update a predefined plan. */
public class PlanRequest {

    @NotBlank @Size(max = 100)
    private String planName;

    @Size(max = 2000)
    private String description;

    private PlanStatus status = PlanStatus.ACTIVE;

    /** Feature codes enabled on this plan. Unknown codes are rejected. */
    @NotNull
    private List<@NotBlank @Size(max = 64) String> featureCodes = new ArrayList<>();

    @Valid @NotNull
    private LimitsDto limits;

    /** One entry per priority; missing priorities fall back to app.subscription.custom-default-sla. */
    @Valid @NotNull
    private List<SlaDto> sla = new ArrayList<>();

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public PlanStatus getStatus() { return status; }
    public void setStatus(PlanStatus status) { this.status = status; }
    public List<String> getFeatureCodes() { return featureCodes; }
    public void setFeatureCodes(List<String> featureCodes) { this.featureCodes = featureCodes; }
    public LimitsDto getLimits() { return limits; }
    public void setLimits(LimitsDto limits) { this.limits = limits; }
    public List<SlaDto> getSla() { return sla; }
    public void setSla(List<SlaDto> sla) { this.sla = sla; }
}
