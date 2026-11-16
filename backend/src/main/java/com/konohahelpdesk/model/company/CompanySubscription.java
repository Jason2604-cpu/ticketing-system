package com.konohahelpdesk.model.company;

import com.konohahelpdesk.model.plan.Plan;
import com.konohahelpdesk.model.plan.PlanType;
import com.konohahelpdesk.model.plan.SubscriptionStatus;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// One active subscription configuration per company.
@Entity
@Table(name = "company_subscriptions")
public class CompanySubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    // Null when subscriptionType is CUSTOM with no base plan selected.
    @ManyToOne
    @JoinColumn(name = "plan_id", nullable = true)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_type", nullable = false)
    private PlanType subscriptionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    /**
     * Year-month ("2026-09") of the last "monthly ticket quota reached" alert email, so staff get
     * one warning per company per month instead of one per rejected ticket attempt. Null = never alerted.
     */
    @Column(name = "quota_alert_month", length = 7)
    private String quotaAlertMonth;

    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public Plan getPlan() { return plan; }
    public void setPlan(Plan plan) { this.plan = plan; }

    public PlanType getSubscriptionType() { return subscriptionType; }
    public void setSubscriptionType(PlanType subscriptionType) { this.subscriptionType = subscriptionType; }

    public SubscriptionStatus getStatus() { return status; }
    public void setStatus(SubscriptionStatus status) { this.status = status; }

    public String getQuotaAlertMonth() { return quotaAlertMonth; }
    public void setQuotaAlertMonth(String quotaAlertMonth) { this.quotaAlertMonth = quotaAlertMonth; }

    public LocalDateTime getCreatedDate() { return createdDate; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
