package com.konohahelpdesk.model.company;

import com.konohahelpdesk.model.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Which engineers look after which customer companies (many-to-many). */
@Entity
@Table(name = "engineer_assignments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_engineer_company", columnNames = {"engineer_id", "company_id"})
})
public class EngineerAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "engineer_id", nullable = false)
    private User engineer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public User getEngineer() { return engineer; }
    public void setEngineer(User engineer) { this.engineer = engineer; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
