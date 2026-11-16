package com.konohahelpdesk.controller.report;

import com.konohahelpdesk.dto.report.ReportResponse;

/**
 * Renders a report as CSV.
 *
 * A report isn't one table, so this writes several labelled sections separated by blank lines —
 * which is what spreadsheet users expect from an export like this, and keeps every number that
 * is on screen in the file.
 */
final class ReportCsvWriter {

    private ReportCsvWriter() {}

    static String write(ReportResponse r) {
        StringBuilder sb = new StringBuilder();
        // Excel opens UTF-8 CSV as the local codepage without this marker, mangling any
        // non-ASCII company name.
        sb.append('﻿');

        line(sb, "Ticket Report");
        line(sb, "From", String.valueOf(r.getFrom()));
        line(sb, "To", String.valueOf(r.getTo()));
        line(sb, "Company", r.getCompanyName() == null ? "All companies" : r.getCompanyName());
        sb.append('\n');

        ReportResponse.Totals t = r.getTotals();
        line(sb, "Summary");
        line(sb, "Created", String.valueOf(t.getCreated()));
        line(sb, "Resolved", String.valueOf(t.getResolved()));
        line(sb, "Open", String.valueOf(t.getOpen()));
        line(sb, "In Progress", String.valueOf(t.getInProgress()));
        line(sb, "Closed", String.valueOf(t.getClosed()));
        line(sb, "Avg resolution (business hours)", String.valueOf(t.getAvgResolutionHours()));
        line(sb, "Avg first response (business hours)", String.valueOf(t.getAvgFirstResponseHours()));
        sb.append('\n');

        ReportResponse.Sla s = r.getSla();
        line(sb, "SLA (over tickets created in the period)");
        line(sb, "Tickets measured", String.valueOf(s.getMeasured()));
        line(sb, "Tickets in period", String.valueOf(s.getTotal()));
        line(sb, "Resolution compliance %", String.valueOf(s.getResolutionCompliancePct()));
        line(sb, "Resolution met", String.valueOf(s.getResolutionMet()));
        line(sb, "Resolution missed", String.valueOf(s.getResolutionMissed()));
        line(sb, "Response compliance %", String.valueOf(s.getResponseCompliancePct()));
        line(sb, "Response met", String.valueOf(s.getResponseMet()));
        line(sb, "Response missed", String.valueOf(s.getResponseMissed()));
        line(sb, "Currently breaching (live, ignores the period)", String.valueOf(s.getCurrentlyBreaching()));
        sb.append('\n');

        line(sb, "Tickets per " + ("DAY".equals(r.getGranularity()) ? "day" : "month"));
        line(sb, "Period", "Created", "Resolved");
        for (ReportResponse.Bucket b : r.getSeries()) {
            line(sb, b.period(), String.valueOf(b.created()), String.valueOf(b.resolved()));
        }
        sb.append('\n');

        section(sb, "By status", r.getByStatus());
        section(sb, "By priority", r.getByPriority());
        section(sb, "By category", r.getByCategory());

        line(sb, "By company");
        line(sb, "Company", "Created", "Resolved", "SLA %");
        for (ReportResponse.CompanyRow c : r.getByCompany()) {
            line(sb, c.companyName(), String.valueOf(c.created()), String.valueOf(c.resolved()), String.valueOf(c.slaPct()));
        }
        sb.append('\n');

        line(sb, "By engineer");
        line(sb, "Engineer", "Assigned", "Resolved", "Avg resolution (business hours)");
        for (ReportResponse.EngineerRow e : r.getByEngineer()) {
            line(sb, e.firstName() + " " + e.lastName(), String.valueOf(e.assigned()),
                String.valueOf(e.resolved()), String.valueOf(e.avgResolutionHours()));
        }
        return sb.toString();
    }

    private static void section(StringBuilder sb, String title, java.util.List<ReportResponse.Slice> slices) {
        line(sb, title);
        line(sb, "Name", "Count");
        for (ReportResponse.Slice s : slices) line(sb, s.label(), String.valueOf(s.count()));
        sb.append('\n');
    }

    private static void line(StringBuilder sb, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(cells[i]));
        }
        sb.append('\n');
    }

    /**
     * RFC 4180 quoting, plus a leading apostrophe on anything starting with = + - @, so a company
     * named "=cmd" is shown as text instead of being run as a formula when the file is opened.
     */
    private static String escape(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }
}
