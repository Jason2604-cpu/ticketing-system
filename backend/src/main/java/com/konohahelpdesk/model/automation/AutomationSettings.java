package com.konohahelpdesk.model.automation;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A company's automation switches. The primary key IS the company id (one row per company).
 *
 * Only email-type automations live here. Rules that change how a ticket is handled (such as
 * keyword-based priority) are deliberately not in this table: staff own those.
 */
@Entity
@Table(name = "automation_settings")
public class AutomationSettings {

    @Id
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "monthly_summary_enabled", nullable = false)
    private boolean monthlySummaryEnabled = true;

    /** Day of the month the summary goes out (1-28, so every month has it). */
    @Column(name = "monthly_summary_day", nullable = false)
    private int monthlySummaryDay = 1;

    /** Comma-separated extra recipients on top of the company's Customer Admins. */
    @Column(name = "monthly_summary_extra_recipients", length = 1000)
    private String monthlySummaryExtraRecipients;

    /** When false, only staff may change this company's automation settings. */
    @Column(name = "customer_can_edit", nullable = false)
    private boolean customerCanEdit = true;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected AutomationSettings() {}

    public AutomationSettings(Long companyId) { this.companyId = companyId; }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getCompanyId() { return companyId; }

    public boolean isMonthlySummaryEnabled() { return monthlySummaryEnabled; }
    public void setMonthlySummaryEnabled(boolean v) { this.monthlySummaryEnabled = v; }

    public int getMonthlySummaryDay() { return monthlySummaryDay; }
    public void setMonthlySummaryDay(int v) { this.monthlySummaryDay = v; }

    public String getMonthlySummaryExtraRecipients() { return monthlySummaryExtraRecipients; }
    public void setMonthlySummaryExtraRecipients(String v) { this.monthlySummaryExtraRecipients = v; }

    public boolean isCustomerCanEdit() { return customerCanEdit; }
    public void setCustomerCanEdit(boolean v) { this.customerCanEdit = v; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
