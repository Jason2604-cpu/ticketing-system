package com.konohahelpdesk.model.company;

import jakarta.persistence.*;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * One weekday's opening window for a company. Seven rows per company, one per day.
 *
 * The SLA clock only advances inside these windows, so a ticket raised at 17:55 on a Friday
 * with a 4-hour target is due mid-morning on Monday rather than overnight on Saturday.
 *
 * Times are plain local times, interpreted in the server's timezone — the same basis as every
 * other timestamp in the app (all LocalDateTime). Multi-region deployments would need a
 * per-company zone here and on the ticket timestamps together; doing it on one side only
 * would be worse than not doing it.
 */
@Entity
@Table(name = "company_business_hours",
       uniqueConstraints = @UniqueConstraint(name = "uq_business_hours_company_day",
                                             columnNames = {"company_id", "day_of_week"}))
public class CompanyBusinessHours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    /** Ignored when {@link #closed} is true, but kept so unticking "closed" restores the old times. */
    @Column(name = "open_time", nullable = false)
    private LocalTime openTime = LocalTime.of(9, 0);

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime = LocalTime.of(18, 0);

    /** True = no working minutes accrue on this day at all. */
    @Column(nullable = false)
    private boolean closed = false;

    public Long getId() { return id; }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(DayOfWeek dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public LocalTime getOpenTime() { return openTime; }
    public void setOpenTime(LocalTime openTime) { this.openTime = openTime; }

    public LocalTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalTime closeTime) { this.closeTime = closeTime; }

    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }
}
