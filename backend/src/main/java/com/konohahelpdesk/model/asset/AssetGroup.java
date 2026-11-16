package com.konohahelpdesk.model.asset;

import com.konohahelpdesk.model.company.Company;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A named set of one company's assets — "DC Rack A4", "HQ core network", "Finance laptops" —
 * so a service contract can cover a whole estate in one pick instead of forty.
 *
 * Membership is live, not a snapshot: a contract that covers a group covers whatever is in the
 * group today. Adding a new switch to "HQ core network" puts it under that contract without
 * anyone having to edit the contract.
 *
 * An asset belongs to at most one group, chosen on the asset form. A group only ever holds
 * assets of its own company — AssetService enforces that when the group is picked.
 */
@Entity
@Table(name = "asset_groups", indexes = {
    @Index(name = "ix_asset_groups_company", columnList = "company_id")
})
public class AssetGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** Unique per company, case-insensitively — checked in the service. */
    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /** Read-only side: membership is set on the asset ({@link Asset#getGroup()}), never here. */
    @OneToMany(mappedBy = "group")
    private Set<Asset> assets = new LinkedHashSet<>();

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Set<Asset> getAssets() { return assets; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
