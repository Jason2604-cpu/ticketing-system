package com.konohahelpdesk.dto.subscription;

import com.konohahelpdesk.model.plan.PlanStatus;
import com.konohahelpdesk.model.plan.PlanType;

import java.time.LocalDateTime;
import java.util.List;

public class PlanResponse {
    private Long id;
    private String planName;
    private String description;
    private PlanType planType;
    private PlanStatus status;
    private List<FeatureResponse> features;
    private LimitsDto limits;
    private List<SlaDto> sla;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public PlanType getPlanType() { return planType; }
    public void setPlanType(PlanType planType) { this.planType = planType; }
    public PlanStatus getStatus() { return status; }
    public void setStatus(PlanStatus status) { this.status = status; }
    public List<FeatureResponse> getFeatures() { return features; }
    public void setFeatures(List<FeatureResponse> features) { this.features = features; }
    public LimitsDto getLimits() { return limits; }
    public void setLimits(LimitsDto limits) { this.limits = limits; }
    public List<SlaDto> getSla() { return sla; }
    public void setSla(List<SlaDto> sla) { this.sla = sla; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
