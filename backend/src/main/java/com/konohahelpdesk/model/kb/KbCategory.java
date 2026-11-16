package com.konohahelpdesk.model.kb;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Set;

/** A Knowledge Base category. One level, no nesting. Deleting one leaves its articles Uncategorised. */
@Entity
@Table(name = "kb_categories")
public class KbCategory {

    /**
     * The lucide icon names a category may use. Kept short and server-side so the frontend can map
     * each one to a component without importing the whole icon set.
     */
    public static final Set<String> ICONS = Set.of(
        "BookOpen", "Wrench", "Printer", "Wifi", "Shield", "Mail", "Monitor", "Server", "KeyRound",
        "HardDrive", "Smartphone", "CreditCard", "Users", "Cloud", "Database", "LifeBuoy", "Settings",
        "Network");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 200)
    private String description;

    @Column(length = 40)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
