package com.konohahelpdesk.dto.company;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.konohahelpdesk.dto.subscription.CompanySubscriptionResponse;
import com.konohahelpdesk.model.company.Company;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompanyResponse {
    private Long id;
    private String name;
    private String address;
    private String contactEmail;
    private String contactPhone;
    /** GET this (with the auth cookie) to load the logo; null when none. */
    private String logoUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** Included on create and single-company GET; omitted in list responses. */
    private CompanySubscriptionResponse subscription;

    // ── List-view summary (admin/engineer company table) ─────────────────
    private String planName;
    private String subscriptionType;
    private Long activeUsers;
    private Integer maxUsers;
    private java.util.List<com.konohahelpdesk.dto.user.UserResponse> engineers;
    private com.konohahelpdesk.dto.user.UserResponse companyAdmin;
    /** Engineer/admin notified on every new ticket for this company; null when none set. */
    private com.konohahelpdesk.dto.user.UserResponse mainEngineer;

    /** SLA clock configuration. Populated by CompanyService, not by {@link #from}. */
    private boolean businessHoursEnabled;
    private java.util.List<BusinessHoursDto> businessHours;

    public static CompanyResponse from(Company c) {
        CompanyResponse r = new CompanyResponse();
        r.id = c.getId();
        r.name = c.getName();
        r.address = c.getAddress();
        r.contactEmail = c.getContactEmail();
        r.contactPhone = c.getContactPhone();
        r.logoUrl = c.getLogoId() == null ? null : "/api/media/" + c.getLogoId();
        r.createdAt = c.getCreatedAt();
        r.updatedAt = c.getUpdatedAt();
        r.mainEngineer = c.getMainEngineer() == null ? null : com.konohahelpdesk.dto.user.UserResponse.publicRef(c.getMainEngineer());
        r.businessHoursEnabled = c.isBusinessHoursEnabled();
        return r;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public String getLogoUrl() { return logoUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public CompanySubscriptionResponse getSubscription() { return subscription; }
    public void setSubscription(CompanySubscriptionResponse subscription) { this.subscription = subscription; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public String getSubscriptionType() { return subscriptionType; }
    public void setSubscriptionType(String subscriptionType) { this.subscriptionType = subscriptionType; }
    public Long getActiveUsers() { return activeUsers; }
    public void setActiveUsers(Long activeUsers) { this.activeUsers = activeUsers; }
    public Integer getMaxUsers() { return maxUsers; }
    public void setMaxUsers(Integer maxUsers) { this.maxUsers = maxUsers; }
    public java.util.List<com.konohahelpdesk.dto.user.UserResponse> getEngineers() { return engineers; }
    public void setEngineers(java.util.List<com.konohahelpdesk.dto.user.UserResponse> engineers) { this.engineers = engineers; }
    public com.konohahelpdesk.dto.user.UserResponse getCompanyAdmin() { return companyAdmin; }
    public void setCompanyAdmin(com.konohahelpdesk.dto.user.UserResponse companyAdmin) { this.companyAdmin = companyAdmin; }
    public boolean isBusinessHoursEnabled() { return businessHoursEnabled; }
    public void setBusinessHoursEnabled(boolean businessHoursEnabled) { this.businessHoursEnabled = businessHoursEnabled; }
    public java.util.List<BusinessHoursDto> getBusinessHours() { return businessHours; }
    public void setBusinessHours(java.util.List<BusinessHoursDto> businessHours) { this.businessHours = businessHours; }

    public com.konohahelpdesk.dto.user.UserResponse getMainEngineer() { return mainEngineer; }
    public void setMainEngineer(com.konohahelpdesk.dto.user.UserResponse mainEngineer) { this.mainEngineer = mainEngineer; }
}
