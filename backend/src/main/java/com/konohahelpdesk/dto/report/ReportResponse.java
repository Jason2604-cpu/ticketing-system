package com.konohahelpdesk.dto.report;

import java.time.LocalDate;
import java.util.List;

/**
 * One reporting period, aggregated in SQL rather than in the browser.
 *
 * Every count here is produced by a GROUP BY over the whole matching set, so the numbers
 * stay correct past the ticket list's 500-row page cap — the reason this endpoint exists.
 */
public class ReportResponse {

    /** Inclusive start / exclusive end of the window the numbers cover. */
    private LocalDate from;
    private LocalDate to;
    /** "DAY" or "MONTH" — which bucket size {@link #series} uses. */
    private String granularity;
    private String companyName;

    private Totals totals;
    private List<Bucket> series;
    private List<Slice> byStatus;
    private List<Slice> byPriority;
    private List<Slice> byCategory;
    private Sla sla;
    private List<CompanyRow> byCompany;
    private List<EngineerRow> byEngineer;

    /** Headline numbers for the period. */
    public static class Totals {
        private long created;
        private long resolved;
        private long open;
        private long inProgress;
        private long onHold;
        private long closed;
        /** Mean hours from creation to resolution, over tickets resolved in the period. */
        private double avgResolutionHours;
        /** Mean hours from creation to the first public staff reply. */
        private double avgFirstResponseHours;

        public long getCreated() { return created; }
        public void setCreated(long v) { this.created = v; }
        public long getResolved() { return resolved; }
        public void setResolved(long v) { this.resolved = v; }
        public long getOpen() { return open; }
        public void setOpen(long v) { this.open = v; }
        public long getInProgress() { return inProgress; }
        public void setInProgress(long v) { this.inProgress = v; }
        public long getOnHold() { return onHold; }
        public void setOnHold(long v) { this.onHold = v; }
        public long getClosed() { return closed; }
        public void setClosed(long v) { this.closed = v; }
        public double getAvgResolutionHours() { return avgResolutionHours; }
        public void setAvgResolutionHours(double v) { this.avgResolutionHours = v; }
        public double getAvgFirstResponseHours() { return avgFirstResponseHours; }
        public void setAvgFirstResponseHours(double v) { this.avgFirstResponseHours = v; }
    }

    /** One point on the time series: "2026-09" or "2026-09-04". */
    public record Bucket(String period, long created, long resolved) {}

    /** A named share of the total — status, priority or category. */
    public record Slice(String key, String label, long count) {}

    /**
     * Every figure here is over tickets CREATED in the period — the same population as
     * {@code Totals.created} and every chart on the page. {@code measured} and {@code total} are
     * exposed so the UI can state the denominator instead of showing a bare percentage whose
     * basis nobody can reconstruct.
     */
    public static class Sla {
        /** % of measured tickets that met their resolution target. */
        private int resolutionCompliancePct;
        /** % of tickets that got a first response that met their response target. */
        private int responseCompliancePct;
        private long resolutionMet;
        private long resolutionMissed;
        private long responseMet;
        private long responseMissed;
        /** Tickets in the period whose resolution clock has stopped AND had a target to judge. */
        private long measured;
        /** Tickets created in the period, measured or not. Equals Totals.created. */
        private long total;
        /**
         * Tickets open right now with a breached clock. A LIVE figure — it deliberately ignores the
         * report's date range, so it does not change when you scroll back to last quarter, and it
         * counts tickets where met/missed count clocks.
         */
        private long currentlyBreaching;

        public int getResolutionCompliancePct() { return resolutionCompliancePct; }
        public void setResolutionCompliancePct(int v) { this.resolutionCompliancePct = v; }
        public int getResponseCompliancePct() { return responseCompliancePct; }
        public void setResponseCompliancePct(int v) { this.responseCompliancePct = v; }
        public long getResolutionMet() { return resolutionMet; }
        public void setResolutionMet(long v) { this.resolutionMet = v; }
        public long getResolutionMissed() { return resolutionMissed; }
        public void setResolutionMissed(long v) { this.resolutionMissed = v; }
        public long getResponseMet() { return responseMet; }
        public void setResponseMet(long v) { this.responseMet = v; }
        public long getResponseMissed() { return responseMissed; }
        public void setResponseMissed(long v) { this.responseMissed = v; }
        public long getMeasured() { return measured; }
        public void setMeasured(long v) { this.measured = v; }
        public long getTotal() { return total; }
        public void setTotal(long v) { this.total = v; }
        public long getCurrentlyBreaching() { return currentlyBreaching; }
        public void setCurrentlyBreaching(long v) { this.currentlyBreaching = v; }
    }

    public record CompanyRow(Long companyId, String companyName, long created, long resolved, int slaPct) {}
    public record EngineerRow(Long userId, String firstName, String lastName, long assigned, long resolved, double avgResolutionHours) {}

    public LocalDate getFrom() { return from; }
    public void setFrom(LocalDate from) { this.from = from; }
    public LocalDate getTo() { return to; }
    public void setTo(LocalDate to) { this.to = to; }
    public String getGranularity() { return granularity; }
    public void setGranularity(String granularity) { this.granularity = granularity; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public Totals getTotals() { return totals; }
    public void setTotals(Totals totals) { this.totals = totals; }
    public List<Bucket> getSeries() { return series; }
    public void setSeries(List<Bucket> series) { this.series = series; }
    public List<Slice> getByStatus() { return byStatus; }
    public void setByStatus(List<Slice> byStatus) { this.byStatus = byStatus; }
    public List<Slice> getByPriority() { return byPriority; }
    public void setByPriority(List<Slice> byPriority) { this.byPriority = byPriority; }
    public List<Slice> getByCategory() { return byCategory; }
    public void setByCategory(List<Slice> byCategory) { this.byCategory = byCategory; }
    public Sla getSla() { return sla; }
    public void setSla(Sla sla) { this.sla = sla; }
    public List<CompanyRow> getByCompany() { return byCompany; }
    public void setByCompany(List<CompanyRow> byCompany) { this.byCompany = byCompany; }
    public List<EngineerRow> getByEngineer() { return byEngineer; }
    public void setByEngineer(List<EngineerRow> byEngineer) { this.byEngineer = byEngineer; }
}
