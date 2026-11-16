package com.konohahelpdesk.service.company;

import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.company.CompanyBusinessHours;
import com.konohahelpdesk.repository.company.CompanyBusinessHoursRepository;
import com.konohahelpdesk.repository.company.CompanyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns wall-clock arithmetic into working-hours arithmetic for the SLA clock.
 *
 * A 4-hour response target raised at 17:55 on a Friday should be due mid-morning on Monday, not
 * at 21:55 on Friday night when nobody is on shift. Every SLA deadline is stamped through
 * {@link #addWorkingMinutes} and every "how far through the window are we" ratio through
 * {@link #workingMinutesBetween}, so the two can never disagree.
 *
 * Once stamped, a deadline is an ordinary absolute timestamp — the breach sweep stays a plain
 * "is now past due" comparison, and changing a company's hours never silently rewrites the
 * deadlines of tickets already in flight.
 *
 * Times are the server's local time; see CompanyBusinessHours for why.
 */
@Service
public class BusinessCalendar {

    /** Guard against an infinite walk if a company somehow has no open minutes at all. */
    private static final int MAX_DAYS_SCANNED = 400;

    private final CompanyBusinessHoursRepository hoursRepository;
    private final CompanyRepository companies;

    /**
     * Business hours change roughly never but are read for every ticket in a list response, so
     * they're cached per company. {@link #evict} is called whenever a company is saved.
     *
     * Entries also expire after {@code app.cache.business-hours-ttl-seconds}: evict() only reaches
     * THIS process, so with more than one backend container the others would otherwise keep the
     * old hours until restarted. The TTL bounds that staleness without needing a shared cache.
     */
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();
    private final long ttlMs;

    public BusinessCalendar(CompanyBusinessHoursRepository hoursRepository, CompanyRepository companies,
                            @Value("${app.cache.business-hours-ttl-seconds:300}") long ttlSeconds) {
        this.hoursRepository = hoursRepository;
        this.companies = companies;
        this.ttlMs = Math.max(0, ttlSeconds) * 1000L;
    }

    private record Cached(Schedule schedule, long loadedAt) {}

    public void evict(Long companyId) {
        if (companyId != null) cache.remove(companyId);
    }

    /**
     * {@code start} plus {@code minutes} of working time.
     * Falls back to plain wall-clock when the company has business hours switched off, or when
     * its week contains no open minutes — better a slightly generous deadline than none at all.
     */
    public LocalDateTime addWorkingMinutes(Long companyId, LocalDateTime start, long minutes) {
        Schedule schedule = scheduleFor(companyId);
        if (!schedule.active() || minutes <= 0) return start.plusMinutes(minutes);

        long remaining = minutes;
        LocalDate day = start.toLocalDate();
        LocalDateTime cursor = start;

        for (int scanned = 0; scanned < MAX_DAYS_SCANNED; scanned++) {
            Window window = schedule.windowOn(day);
            if (window != null) {
                LocalDateTime open = LocalDateTime.of(day, window.open());
                LocalDateTime close = LocalDateTime.of(day, window.close());
                LocalDateTime from = cursor.isAfter(open) ? cursor : open;
                if (from.isBefore(close)) {
                    long available = Duration.between(from, close).toMinutes();
                    if (remaining <= available) return from.plusMinutes(remaining);
                    remaining -= available;
                }
            }
            day = day.plusDays(1);
            cursor = day.atStartOfDay();
        }
        // Unreachable for any sane schedule; degrade to the wall clock rather than return a wrong date.
        return start.plusMinutes(minutes);
    }

    /** Working minutes elapsed between two instants. Used for the "at risk" ratio. */
    public long workingMinutesBetween(Long companyId, LocalDateTime from, LocalDateTime to) {
        if (!from.isBefore(to)) return 0;
        Schedule schedule = scheduleFor(companyId);
        if (!schedule.active()) return Duration.between(from, to).toMinutes();

        long total = 0;
        LocalDate day = from.toLocalDate();
        LocalDate last = to.toLocalDate();
        for (int scanned = 0; !day.isAfter(last) && scanned < MAX_DAYS_SCANNED; scanned++, day = day.plusDays(1)) {
            Window window = schedule.windowOn(day);
            if (window == null) continue;
            LocalDateTime open = LocalDateTime.of(day, window.open());
            LocalDateTime close = LocalDateTime.of(day, window.close());
            LocalDateTime segmentStart = from.isAfter(open) ? from : open;
            LocalDateTime segmentEnd = to.isBefore(close) ? to : close;
            if (segmentStart.isBefore(segmentEnd)) {
                total += Duration.between(segmentStart, segmentEnd).toMinutes();
            }
        }
        return total;
    }

    /** True when this company's SLA clock is running right now. Drives the "outside hours" hint. */
    public boolean isWithinBusinessHours(Long companyId, LocalDateTime at) {
        Schedule schedule = scheduleFor(companyId);
        if (!schedule.active()) return true;
        Window window = schedule.windowOn(at.toLocalDate());
        if (window == null) return false;
        LocalTime time = at.toLocalTime();
        return !time.isBefore(window.open()) && time.isBefore(window.close());
    }

    // ── loading ──────────────────────────────────────────────────────────────

    private Schedule scheduleFor(Long companyId) {
        if (companyId == null) return Schedule.INACTIVE;
        long now = System.currentTimeMillis();
        Cached c = cache.get(companyId);
        if (c == null || now - c.loadedAt() > ttlMs) {
            c = new Cached(load(companyId), now);
            cache.put(companyId, c);
        }
        return c.schedule();
    }

    private Schedule load(Long companyId) {
        boolean enabled = companies.findById(companyId).map(Company::isBusinessHoursEnabled).orElse(false);
        if (!enabled) return Schedule.INACTIVE;

        Map<DayOfWeek, Window> byDay = new EnumMap<>(DayOfWeek.class);
        List<CompanyBusinessHours> rows = hoursRepository.findByCompanyId(companyId);
        for (CompanyBusinessHours row : rows) {
            if (row.isClosed()) continue;
            if (!row.getOpenTime().isBefore(row.getCloseTime())) continue;   // malformed row: ignore
            byDay.put(row.getDayOfWeek(), new Window(row.getOpenTime(), row.getCloseTime()));
        }
        // Enabled but every day closed would make deadlines unreachable — treat as 24/7 instead.
        return byDay.isEmpty() ? Schedule.INACTIVE : new Schedule(byDay);
    }

    private record Window(LocalTime open, LocalTime close) {}

    private record Schedule(Map<DayOfWeek, Window> byDay) {
        static final Schedule INACTIVE = new Schedule(Map.of());

        boolean active() { return !byDay.isEmpty(); }

        Window windowOn(LocalDate date) { return byDay.get(date.getDayOfWeek()); }
    }
}
