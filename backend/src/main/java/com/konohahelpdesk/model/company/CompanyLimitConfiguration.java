package com.konohahelpdesk.model.company;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "company_limit_configurations")
public class CompanyLimitConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    @Column(name = "max_users", nullable = false)
    private Integer maxUsers;

    @Column(name = "monthly_ticket_limit", nullable = false)
    private Integer monthlyTicketLimit;

    // Megabytes
    @Column(name = "storage_limit_mb", nullable = false)
    private Long storageLimitMb;

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

    public Integer getMaxUsers() { return maxUsers; }
    public void setMaxUsers(Integer maxUsers) { this.maxUsers = maxUsers; }

    public Integer getMonthlyTicketLimit() { return monthlyTicketLimit; }
    public void setMonthlyTicketLimit(Integer monthlyTicketLimit) { this.monthlyTicketLimit = monthlyTicketLimit; }

    public Long getStorageLimitMb() { return storageLimitMb; }
    public void setStorageLimitMb(Long storageLimitMb) { this.storageLimitMb = storageLimitMb; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
