package com.konohahelpdesk.service.report;

import com.konohahelpdesk.dto.report.ReportResponse;
import com.konohahelpdesk.service.company.BusinessCalendar;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.ticket.TicketCategory;
import com.konohahelpdesk.model.ticket.TicketPriority;
import com.konohahelpdesk.model.ticket.TicketStatus;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.dto.user.UserResponse;
import com.konohahelpdesk.service.company.EngineerAssignmentService;
import com.konohahelpdesk.repository.company.CompanyRepository;
import com.konohahelpdesk.repository.ticket.TicketReportRepository;
import com.konohahelpdesk.service.subscription.CompanySubscriptionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds a {@link ReportResponse} for a date range, entirely from database aggregates.
 *
 * Access rules, all resolved in {@link #visibleCompanies} into the exact id list the queries run on:
 *  - ADMIN: every company, or one if they ask for it.
 *  - ENGINEER: only the companies assigned to them — the same rule the ticket list uses, so a
 *    report can't reveal what the engineer's own ticket list won't. An engineer with NO assignments
 *    is unrestricted, again matching TicketService.engineerScope.
 *  - CUSTOMER / CUSTOMER_ADMIN: their own company only, and only when its plan carries REPORTING.
 *
 * The CSV export goes through the same method, so a download can never be wider than the page.
 */
@Service
public class ReportService {

    /** Matches no row — used when a caller legitimately has access to nothing, since SQL has no IN (). */
    private static final List<Long> NOTHING = List.of(-1L);
    /** Above this many days the series switches from daily to monthly buckets. */
    private static final long DAILY_BUCKET_MAX_DAYS = 92;
    private static final int MAX_RANGE_DAYS = 366 * 3;

    private final TicketReportRepository reports;
    private final CompanyRepository companies;
    private final CompanySubscriptionService subscriptions;
    private final EngineerAssignmentService assignments;
    /** The same calendar that stamped every deadline — see Durations below for why it's here. */
    private final BusinessCalendar calendar;

    public ReportService(TicketReportRepository reports, CompanyRepository companies,
                         CompanySubscriptionService subscriptions, EngineerAssignmentService assignments,
                         BusinessCalendar calendar) {
        this.reports = reports;
        this.companies = companies;
        this.subscriptions = subscriptions;
        this.assignments = assignments;
        this.calendar = calendar;
    }

    /**
     * Working-hour durations for one window, measured through {@link BusinessCalendar} so they
     * agree with the SLA deadlines they sit next to on the page.
     *
     * @param avgResolutionHours    mean creation → finished, working hours
     * @param avgFirstResponseHours mean creation → first response, working hours
     * @param avgResolutionByEngineer per-assignee mean resolution, working hours
     */
    private record Durations(double avgResolutionHours, double avgFirstResponseHours,
                             Map<Long, Double> avgResolutionByEngineer) {}

    /**
     * One pass over the window's raw timestamps. Wall-clock arithmetic in SQL would report a
     * ticket raised on Friday evening and answered on Monday morning as 64 hours, while its SLA —
     * measured in working minutes — says one hour and comfortably met. Averages computed that way
     * cannot be reconciled with the compliance figure printed beside them, which is what made this
     * page look wrong.
     */
    private Durations durations(List<Long> companyIds, LocalDateTime from, LocalDateTime to) {
        long resolutionSum = 0, resolutionCount = 0, responseSum = 0, responseCount = 0;
        Map<Long, long[]> perEngineer = new LinkedHashMap<>();   // assigneeId -> [sumMinutes, count]

        for (Object[] row : reports.durationRows(companyIds, from, to)) {
            Long companyId = row[0] == null ? null : ((Number) row[0]).longValue();
            Long assigneeId = row[1] == null ? null : ((Number) row[1]).longValue();
            LocalDateTime created = ts(row[2]);
            LocalDateTime firstResponse = ts(row[3]);
            LocalDateTime stopped = ts(row[4]);
            if (created == null) continue;

            if (firstResponse != null) {
                responseSum += calendar.workingMinutesBetween(companyId, created, firstResponse);
                responseCount++;
            }
            if (stopped != null) {
                long minutes = calendar.workingMinutesBetween(companyId, created, stopped);
                resolutionSum += minutes;
                resolutionCount++;
                if (assigneeId != null) {
                    long[] acc = perEngineer.computeIfAbsent(assigneeId, k -> new long[2]);
                    acc[0] += minutes;
                    acc[1]++;
                }
            }
        }

        Map<Long, Double> byEngineer = new LinkedHashMap<>();
        perEngineer.forEach((id, acc) -> byEngineer.put(id, acc[1] == 0 ? 0d : acc[0] / 60.0 / acc[1]));

        return new Durations(
            resolutionCount == 0 ? 0d : resolutionSum / 60.0 / resolutionCount,
            responseCount == 0 ? 0d : responseSum / 60.0 / responseCount,
            byEngineer);
    }

    /** Native queries hand back java.sql.Timestamp; nulls stay null. */
    private static LocalDateTime ts(Object o) {
        if (o == null) return null;
        if (o instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        if (o instanceof LocalDateTime d) return d;
        return null;
    }

    /**
     * @param from inclusive start date; defaults to 30 days before {@code to}
     * @param to   inclusive end date (the query uses the day after, exclusive); defaults to today
     * @param companyId null = all companies (staff only)
     */
    public ReportResponse build(User actor, LocalDate from, LocalDate to, Long companyId) {
        List<Long> scoped = visibleCompanies(actor, companyId);

        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(29);
        if (start.isAfter(end)) throw new BusinessRuleException("The start date must not be after the end date");
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new BusinessRuleException("Report range is too long (max " + MAX_RANGE_DAYS + " days)");
        }

        LocalDateTime fromTs = start.atStartOfDay();
        LocalDateTime toTs = end.plusDays(1).atStartOfDay();   // half-open: includes all of `end`
        boolean daily = ChronoUnit.DAYS.between(start, end) <= DAILY_BUCKET_MAX_DAYS;

        ReportResponse r = new ReportResponse();
        r.setFrom(start);
        r.setTo(end);
        r.setGranularity(daily ? "DAY" : "MONTH");
        // Name the company only when the report is about exactly one — otherwise the header
        // would claim a single company for what is really a multi-company roll-up.
        if (scoped.size() == 1 && !scoped.equals(NOTHING)) {
            r.setCompanyName(companies.findById(scoped.get(0)).map(Company::getName).orElse(null));
        }

        // One pass over the window's timestamps, shared by the totals and the engineer table so
        // the two can't drift apart the way the SQL averages did.
        Durations durations = durations(scoped, fromTs, toTs);

        r.setTotals(totals(scoped, fromTs, toTs, durations));
        r.setSeries(series(scoped, fromTs, toTs, start, end, daily));
        r.setByStatus(statusSlices(scoped, fromTs, toTs));
        r.setByPriority(prioritySlices(scoped, fromTs, toTs));
        r.setByCategory(categorySlices(scoped, fromTs, toTs));
        r.setSla(sla(scoped, fromTs, toTs));
        r.setByCompany(companyRows(scoped, fromTs, toTs));
        r.setByEngineer(engineerRows(scoped, fromTs, toTs, durations));
        return r;
    }

    /**
     * The exact company ids this user may report on, after applying the requested filter.
     * Never empty — {@link #NOTHING} stands in, so the queries return zeroes instead of failing.
     */
    private List<Long> visibleCompanies(User actor, Long requested) {
        if (actor.getRole() == Roles.ADMIN) {
            if (requested != null) return List.of(requested);
            return allCompanyIds();
        }

        if (actor.getRole() == Roles.ENGINEER) {
            List<Long> assigned = assignments.companiesForEngineer(actor.getId()).stream()
                .map(UserResponse.CompanyRef::id).toList();
            // No assignments = unrestricted, the same convention TicketService.engineerScope uses.
            if (assigned.isEmpty()) return requested != null ? List.of(requested) : allCompanyIds();
            if (requested == null) return assigned;
            if (!assigned.contains(requested)) {
                throw new AccessDeniedException("You are not assigned to that company");
            }
            return List.of(requested);
        }

        Long own = actor.getCompany() == null ? null : actor.getCompany().getId();
        if (own == null) throw new AccessDeniedException("Your account is not linked to a company");
        if (requested != null && !requested.equals(own)) {
            throw new AccessDeniedException("You can only report on your own company");
        }
        // Paid feature — a Starter customer gets a 403 here, not an empty report.
        subscriptions.requireFeature(own, "REPORTING");
        return List.of(own);
    }

    private List<Long> allCompanyIds() {
        List<Long> ids = companies.findAll().stream().map(Company::getId).toList();
        return ids.isEmpty() ? NOTHING : ids;
    }

    private ReportResponse.Totals totals(List<Long> companyIds, LocalDateTime from, LocalDateTime to, Durations d) {
        ReportResponse.Totals t = new ReportResponse.Totals();
        t.setCreated(reports.countCreated(companyIds, from, to));
        t.setResolved(reports.countResolved(companyIds, from, to));
        Map<TicketStatus, Long> byStatus = statusCounts(companyIds, from, to);
        t.setOpen(byStatus.getOrDefault(TicketStatus.OPEN, 0L));
        t.setInProgress(byStatus.getOrDefault(TicketStatus.IN_PROGRESS, 0L));
        t.setClosed(byStatus.getOrDefault(TicketStatus.CLOSED, 0L));
        // WORKING hours, not elapsed — see Durations.
        t.setAvgResolutionHours(round1(d.avgResolutionHours()));
        t.setAvgFirstResponseHours(round1(d.avgFirstResponseHours()));
        return t;
    }

    /**
     * Merges the created and resolved series and fills empty buckets, so a month with no
     * activity shows as a zero on the chart rather than vanishing and distorting the axis.
     */
    private List<ReportResponse.Bucket> series(List<Long> companyIds, LocalDateTime from, LocalDateTime to,
                                               LocalDate start, LocalDate end, boolean daily) {
        String fmt = daily ? "YYYY-MM-DD" : "YYYY-MM";
        Map<String, Long> created = toCountMap(reports.createdSeries(companyIds, fmt, from, to));
        Map<String, Long> resolved = toCountMap(reports.resolvedSeries(companyIds, fmt, from, to));

        List<ReportResponse.Bucket> out = new ArrayList<>();
        if (daily) {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                String key = d.format(f);
                out.add(new ReportResponse.Bucket(key, created.getOrDefault(key, 0L), resolved.getOrDefault(key, 0L)));
            }
        } else {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM");
            YearMonth last = YearMonth.from(end);
            for (YearMonth m = YearMonth.from(start); !m.isAfter(last); m = m.plusMonths(1)) {
                String key = m.atDay(1).format(f);
                out.add(new ReportResponse.Bucket(key, created.getOrDefault(key, 0L), resolved.getOrDefault(key, 0L)));
            }
        }
        return out;
    }

    private Map<TicketStatus, Long> statusCounts(List<Long> companyIds, LocalDateTime from, LocalDateTime to) {
        Map<TicketStatus, Long> map = new LinkedHashMap<>();
        for (Object[] row : reports.groupByStatus(companyIds, from, to)) {
            map.put((TicketStatus) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    private List<ReportResponse.Slice> statusSlices(List<Long> companyIds, LocalDateTime from, LocalDateTime to) {
        Map<TicketStatus, Long> counts = statusCounts(companyIds, from, to);
        List<ReportResponse.Slice> out = new ArrayList<>();
        for (TicketStatus s : TicketStatus.values()) {
            out.add(new ReportResponse.Slice(s.name(), pretty(s.name()), counts.getOrDefault(s, 0L)));
        }
        return out;
    }

    private List<ReportResponse.Slice> prioritySlices(List<Long> companyIds, LocalDateTime from, LocalDateTime to) {
        Map<TicketPriority, Long> counts = new LinkedHashMap<>();
        for (Object[] row : reports.groupByPriority(companyIds, from, to)) {
            counts.put((TicketPriority) row[0], ((Number) row[1]).longValue());
        }
        List<ReportResponse.Slice> out = new ArrayList<>();
        for (TicketPriority p : TicketPriority.values()) {
            out.add(new ReportResponse.Slice(p.name(), pretty(p.name()), counts.getOrDefault(p, 0L)));
        }
        return out;
    }

    private List<ReportResponse.Slice> categorySlices(List<Long> companyIds, LocalDateTime from, LocalDateTime to) {
        Map<TicketCategory, Long> counts = new LinkedHashMap<>();
        for (Object[] row : reports.groupByCategory(companyIds, from, to)) {
            counts.put((TicketCategory) row[0], ((Number) row[1]).longValue());
        }
        List<ReportResponse.Slice> out = new ArrayList<>();
        for (TicketCategory c : TicketCategory.values()) {
            out.add(new ReportResponse.Slice(c.name(), pretty(c.name()), counts.getOrDefault(c, 0L)));
        }
        return out;
    }

    private ReportResponse.Sla sla(List<Long> companyIds, LocalDateTime from, LocalDateTime to) {
        ReportResponse.Sla s = new ReportResponse.Sla();
        // One "now" for the whole block, so a deadline that lapses mid-request can't make the
        // met/missed columns and the measured column disagree by one.
        List<Object[]> rows = reports.slaCounts(companyIds, from, to, LocalDateTime.now());
        Object[] row = rows.isEmpty() ? new Object[]{0L, 0L, 0L, 0L, 0L, 0L} : rows.get(0);
        long resMet = num(row[0]), resMissed = num(row[1]), respMet = num(row[2]), respMissed = num(row[3]);
        s.setResolutionMet(resMet);
        s.setResolutionMissed(resMissed);
        s.setResponseMet(respMet);
        s.setResponseMissed(respMissed);
        // The denominators, so the UI can print "28 of 31 measured" rather than a bare percentage.
        s.setMeasured(row.length > 4 ? num(row[4]) : resMet + resMissed);
        s.setTotal(row.length > 5 ? num(row[5]) : 0L);
        s.setResolutionCompliancePct(pct(resMet, resMet + resMissed));
        s.setResponseCompliancePct(pct(respMet, respMet + respMissed));
        s.setCurrentlyBreaching(reports.countCurrentlyBreaching(companyIds, LocalDateTime.now()));
        return s;
    }

    private List<ReportResponse.CompanyRow> companyRows(List<Long> companyIds, LocalDateTime from, LocalDateTime to) {
        List<ReportResponse.CompanyRow> out = new ArrayList<>();
        for (Object[] row : reports.groupByCompany(companyIds, from, to, LocalDateTime.now())) {
            long met = num(row[4]), total = num(row[5]);
            out.add(new ReportResponse.CompanyRow(
                ((Number) row[0]).longValue(), (String) row[1],
                num(row[2]), num(row[3]), pct(met, total)));
        }
        return out;
    }

    private List<ReportResponse.EngineerRow> engineerRows(List<Long> companyIds, LocalDateTime from, LocalDateTime to, Durations d) {
        List<ReportResponse.EngineerRow> out = new ArrayList<>();
        for (Object[] row : reports.groupByEngineer(companyIds, from, to)) {
            long userId = ((Number) row[0]).longValue();
            out.add(new ReportResponse.EngineerRow(
                userId, (String) row[1], (String) row[2],
                num(row[3]), num(row[4]),
                round1(d.avgResolutionByEngineer().getOrDefault(userId, 0d))));
        }
        return out;
    }

    // ── small helpers ────────────────────────────────────────────────────────

    private static Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) map.put((String) row[0], ((Number) row[1]).longValue());
        return map;
    }

    private static long num(Object o) { return o == null ? 0L : ((Number) o).longValue(); }

    private static double orZero(Double d) { return d == null ? 0d : d; }

    private static double round1(double d) { return Math.round(d * 10.0) / 10.0; }

    /** Compliance with nothing measured yet reads as 100%, not 0% — no data isn't a failure. */
    private static int pct(long met, long total) {
        return total == 0 ? 100 : (int) Math.round(met * 100.0 / total);
    }

    /** IN_PROGRESS → "In Progress". */
    private static String pretty(String enumName) {
        String[] parts = enumName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
