package com.konohahelpdesk.dto.subscription;

import com.konohahelpdesk.model.plan.PlanType;
import com.konohahelpdesk.model.plan.SubscriptionStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The fully-resolved subscription for a company: whichever source the values come
 * from (plan or custom config), the frontend gets one flat, ready-to-render view.
 */
public class CompanySubscriptionResponse {
    private Long companyId;
    private String companyName;
    private PlanType subscriptionType;
    private SubscriptionStatus status;
    private Long planId;
    private String planName;
    /** true = fields are inherited from a plan and cannot be edited on the company. */
    private boolean locked;
    private List<FeatureResponse> features;
    private LimitsDto limits;
    private List<SlaDto> sla;
    private UsageDto usage;
    private LocalDateTime createdDate;
    private LocalDateTime updatedAt;

    /** What the company has actually consumed, against {@link #limits}. */
    public static class UsageDto {
        private long activeUsers;
        private long storageUsedBytes;
        private long ticketsThisMonth;

        public long getActiveUsers() { return activeUsers; }
        public void setActiveUsers(long activeUsers) { this.activeUsers = activeUsers; }
        public long getStorageUsedBytes() { return storageUsedBytes; }
        public void setStorageUsedBytes(long storageUsedBytes) { this.storageUsedBytes = storageUsedBytes; }
        public long getTicketsThisMonth() { return ticketsThisMonth; }
        public void setTicketsThisMonth(long ticketsThisMonth) { this.ticketsThisMonth = ticketsThisMonth; }
    }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public PlanType getSubscriptionType() { return subscriptionType; }
    public void setSubscriptionType(PlanType subscriptionType) { this.subscriptionType = subscriptionType; }
    public SubscriptionStatus getStatus() { return status; }
    public void setStatus(SubscriptionStatus status) { this.status = status; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public List<FeatureResponse> getFeatures() { return features; }
    public void setFeatures(List<FeatureResponse> features) { this.features = features; }
    public LimitsDto getLimits() { return limits; }
    public void setLimits(LimitsDto limits) { this.limits = limits; }
    public List<SlaDto> getSla() { return sla; }
    public void setSla(List<SlaDto> sla) { this.sla = sla; }
    public UsageDto getUsage() { return usage; }
    public void setUsage(UsageDto usage) { this.usage = usage; }
    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
