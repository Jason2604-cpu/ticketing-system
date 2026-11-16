package com.konohahelpdesk.model.automation;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** One scheduled automation's outcome for one company and one period (e.g. MONTHLY_SUMMARY, "2026-10"). */
@Entity
@Table(name = "automation_runs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_automation_run", columnNames = {"company_id", "automation_type", "period_key"})
})
public class AutomationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "automation_type", nullable = false, length = 40)
    private AutomationType automationType;

    @Column(name = "period_key", nullable = false, length = 20)
    private String periodKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RunStatus status;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @Column(nullable = false)
    private int attempts = 1;

    @Column(length = 500)
    private String detail;

    @Column(name = "ran_at", nullable = false)
    private LocalDateTime ranAt;

    protected AutomationRun() {}

    public AutomationRun(Long companyId, AutomationType type, String periodKey) {
        this.companyId = companyId;
        this.automationType = type;
        this.periodKey = periodKey;
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public AutomationType getAutomationType() { return automationType; }
    public String getPeriodKey() { return periodKey; }

    public RunStatus getStatus() { return status; }
    public int getRecipientCount() { return recipientCount; }
    public int getAttempts() { return attempts; }
    public String getDetail() { return detail; }
    public LocalDateTime getRanAt() { return ranAt; }

    /** Records an attempt's outcome. Detail is clipped to the column so a long error can't fail the save. */
    public void record(RunStatus status, int recipients, String detail) {
        this.status = status;
        this.recipientCount = recipients;
        this.detail = detail == null ? null : (detail.length() > 500 ? detail.substring(0, 500) : detail);
        this.ranAt = LocalDateTime.now();
    }

    public void countRetry() { this.attempts++; }
}
