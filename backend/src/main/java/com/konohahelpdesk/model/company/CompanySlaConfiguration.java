package com.konohahelpdesk.model.company;

import com.konohahelpdesk.model.plan.SlaPriority;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "company_sla_configurations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_company_sla_priority", columnNames = {"company_id", "priority"})
})
public class CompanySlaConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlaPriority priority;

    @Column(name = "response_time_minutes", nullable = false)
    private Integer responseTimeMinutes;

    @Column(name = "resolution_time_minutes", nullable = false)
    private Integer resolutionTimeMinutes;

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

    public SlaPriority getPriority() { return priority; }
    public void setPriority(SlaPriority priority) { this.priority = priority; }

    public Integer getResponseTimeMinutes() { return responseTimeMinutes; }
    public void setResponseTimeMinutes(Integer responseTimeMinutes) { this.responseTimeMinutes = responseTimeMinutes; }

    public Integer getResolutionTimeMinutes() { return resolutionTimeMinutes; }
    public void setResolutionTimeMinutes(Integer resolutionTimeMinutes) { this.resolutionTimeMinutes = resolutionTimeMinutes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
