package com.konohahelpdesk.model.company;

import com.konohahelpdesk.model.plan.Feature;
import com.konohahelpdesk.model.plan.Plan;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// Per-company feature toggles for Custom Plan companies (and ad-hoc overrides
// on top of a predefined plan, if ever allowed).
@Entity
@Table(name = "company_subscription_feature_overrides", uniqueConstraints = {
        @UniqueConstraint(name = "uk_company_feature_override", columnNames = {"company_id", "feature_id"})
})
public class CompanySubscriptionFeatureOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(optional = false)
    @JoinColumn(name = "feature_id", nullable = false)
    private Feature feature;

    @Column(nullable = false)
    private boolean enabled;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public Feature getFeature() { return feature; }
    public void setFeature(Feature feature) { this.feature = feature; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
