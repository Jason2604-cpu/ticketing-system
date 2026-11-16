package com.konohahelpdesk.model.plan;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_name", nullable = false, unique = true)
    private String planName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false)
    private PlanType planType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status = PlanStatus.ACTIVE;

    // Limits every company on this plan inherits (read-only for the company).
    @Column(name = "max_users", nullable = false)
    private Integer maxUsers = 10;

    @Column(name = "monthly_ticket_limit", nullable = false)
    private Integer monthlyTicketLimit = 100;

    @Column(name = "storage_limit_mb", nullable = false)
    private Long storageLimitMb = 1024L;

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

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public PlanType getPlanType() { return planType; }
    public void setPlanType(PlanType planType) { this.planType = planType; }

    public PlanStatus getStatus() { return status; }
    public void setStatus(PlanStatus status) { this.status = status; }

    public Integer getMaxUsers() { return maxUsers; }
    public void setMaxUsers(Integer maxUsers) { this.maxUsers = maxUsers; }

    public Integer getMonthlyTicketLimit() { return monthlyTicketLimit; }
    public void setMonthlyTicketLimit(Integer monthlyTicketLimit) { this.monthlyTicketLimit = monthlyTicketLimit; }

    public Long getStorageLimitMb() { return storageLimitMb; }
    public void setStorageLimitMb(Long storageLimitMb) { this.storageLimitMb = storageLimitMb; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
