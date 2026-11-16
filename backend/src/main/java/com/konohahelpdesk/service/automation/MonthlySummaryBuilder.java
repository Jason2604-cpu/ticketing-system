package com.konohahelpdesk.service.automation;

import com.konohahelpdesk.dto.report.ReportResponse;
import com.konohahelpdesk.mail.MailService;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.ticket.TicketStatus;
import com.konohahelpdesk.repository.company.CompanyRepository;
import com.konohahelpdesk.repository.ticket.TicketReportRepository;
import com.konohahelpdesk.repository.ticket.TicketRepository;
import com.konohahelpdesk.service.report.ReportService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Renders the monthly ticket summary a company's Customer Admins receive.
 *
 * The numbers come from {@link ReportService}, the same code behind the Reports page, so the email
 * and the page can't disagree. Everything is measured over tickets CREATED in the month (the
 * report's own definition); "still open" is the live count as of when the email is built.
 */
@Component
public class MonthlySummaryBuilder {

    /** A rendered email: HTML for mail clients that show it, plain text for the rest. */
    public record Built(String subject, String html, String text, String periodLabel) {}

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final List<TicketStatus> OPEN_STATUSES = List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS);
    private static final int TOP_RAISERS = 5;

    private final ReportService reports;
    private final TicketReportRepository reportRepo;
    private final TicketRepository tickets;
    private final CompanyRepository companies;
    private final AutomationClock clock;
    private final MailService mail;

    public MonthlySummaryBuilder(ReportService reports, TicketReportRepository reportRepo, TicketRepository tickets,
                                 CompanyRepository companies, AutomationClock clock, MailService mail) {
        this.reports = reports;
        this.reportRepo = reportRepo;
        this.tickets = tickets;
        this.companies = companies;
        this.clock = clock;
        this.mail = mail;
    }

    @Transactional(readOnly = true)
    public Built build(Long companyId, YearMonth month) {
        Company company = companies.findById(companyId).orElseThrow();
        YearMonth prevMonth = month.minusMonths(1);

        ReportResponse cur = forMonth(companyId, month);
        ReportResponse prev = forMonth(companyId, prevMonth);
        long openNow = tickets.countByCompanyIdAndStatusIn(companyId, OPEN_STATUSES);
        LocalDateTime from = clock.startOf(month);
        LocalDateTime to = clock.startOf(month.plusMonths(1));
        List<Object[]> raisers = reportRepo.topCreators(companyId, from, to, PageRequest.of(0, TOP_RAISERS));

        String period = month.format(MONTH);
        String prevLabel = prevMonth.format(MONTH);
        String subject = mail.appName() + " — " + company.getName() + " ticket summary for " + period;
        return new Built(subject,
            html(company.getName(), period, prevLabel, cur, prev, openNow, raisers),
            text(company.getName(), period, prevLabel, cur, prev, openNow, raisers),
            period);
    }

    private ReportResponse forMonth(Long companyId, YearMonth m) {
        return reports.buildForCompany(companyId, m.atDay(1), m.atEndOfMonth(), clock.startOf(m), clock.startOf(m.plusMonths(1)));
    }

    // ── Plain text ───────────────────────────────────────────────────────────

    private String text(String company, String period, String prevLabel, ReportResponse r, ReportResponse prev,
                        long openNow, List<Object[]> raisers) {
        ReportResponse.Totals t = r.getTotals();
        StringBuilder b = new StringBuilder();
        b.append(company).append(" — ticket summary for ").append(period).append("\n\n");
        b.append("Tickets raised:    ").append(t.getCreated()).append("  (").append(delta(t.getCreated(), prev.getTotals().getCreated(), prevLabel)).append(")\n");
        b.append("Tickets resolved:  ").append(t.getResolved()).append("\n");
        b.append("Still open now:    ").append(openNow).append("\n");
        b.append("Avg first response: ").append(hours(t.getAvgFirstResponseHours(), t.getCreated())).append(" (working hours)\n");
        b.append("Avg resolution:     ").append(hours(t.getAvgResolutionHours(), t.getResolved())).append(" (working hours)\n");
        ReportResponse.Sla sla = r.getSla();
        b.append("SLA response met:   ").append(pct(sla.getResponseCompliancePct(), sla.getResponseMet() + sla.getResponseMissed())).append("\n");
        b.append("SLA resolution met: ").append(pct(sla.getResolutionCompliancePct(), sla.getResolutionMet() + sla.getResolutionMissed())).append("\n");
        slices(b, "By priority", r.getByPriority());
        slices(b, "By status", r.getByStatus());
        slices(b, "By category", r.getByCategory());
        if (!raisers.isEmpty()) {
            b.append("\nMost tickets raised by\n");
            for (Object[] row : raisers) b.append("  ").append(name(row)).append(": ").append(((Number) row[3]).longValue()).append("\n");
        }
        b.append("\nOpen your tickets: ").append(mail.appLink("/tickets")).append("\n");
        b.append("Change or stop this email: ").append(mail.appLink("/automation")).append("\n");
        return b.toString();
    }

    private static void slices(StringBuilder b, String title, List<ReportResponse.Slice> list) {
        List<ReportResponse.Slice> nonZero = list.stream().filter(s -> s.count() > 0).toList();
        if (nonZero.isEmpty()) return;
        b.append("\n").append(title).append("\n");
        for (ReportResponse.Slice s : nonZero) b.append("  ").append(s.label()).append(": ").append(s.count()).append("\n");
    }

    // ── HTML ─────────────────────────────────────────────────────────────────

    private String html(String company, String period, String prevLabel, ReportResponse r, ReportResponse prev,
                        long openNow, List<Object[]> raisers) {
        ReportResponse.Totals t = r.getTotals();
        ReportResponse.Sla sla = r.getSla();
        StringBuilder b = new StringBuilder(4096);
        b.append("<!DOCTYPE html><html><body style=\"margin:0;padding:0;background:#f4f5f7;font-family:Segoe UI,Arial,sans-serif;color:#1f2937;\">")
         .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\" style=\"padding:24px 12px;\">")
         .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:600px;width:100%;background:#ffffff;border-radius:8px;border:1px solid #e5e7eb;\">");

        b.append("<tr><td style=\"padding:24px 28px 8px;\"><div style=\"font-size:13px;color:#6b7280;\">").append(esc(mail.appName())).append("</div>")
         .append("<div style=\"font-size:20px;font-weight:600;margin-top:4px;\">Ticket summary for ").append(esc(period)).append("</div>")
         .append("<div style=\"font-size:14px;color:#374151;margin-top:2px;\">").append(esc(company)).append("</div></td></tr>");

        // Headline tiles
        b.append("<tr><td style=\"padding:12px 22px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>")
         .append(tile("Raised", String.valueOf(t.getCreated()), delta(t.getCreated(), prev.getTotals().getCreated(), prevLabel)))
         .append(tile("Resolved", String.valueOf(t.getResolved()), ""))
         .append(tile("Still open", String.valueOf(openNow), "as of now"))
         .append("</tr></table></td></tr>");

        // Service levels
        b.append("<tr><td style=\"padding:4px 28px 0;\"><div style=\"font-size:14px;font-weight:600;margin:12px 0 6px;\">Service levels</div>")
         .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"font-size:14px;\">")
         .append(row("Average first response", hours(t.getAvgFirstResponseHours(), t.getCreated()) + " working hours"))
         .append(row("Average resolution time", hours(t.getAvgResolutionHours(), t.getResolved()) + " working hours"))
         .append(row("Response target met", pct(sla.getResponseCompliancePct(), sla.getResponseMet() + sla.getResponseMissed())))
         .append(row("Resolution target met", pct(sla.getResolutionCompliancePct(), sla.getResolutionMet() + sla.getResolutionMissed())))
         .append("</table></td></tr>");

        section(b, "By priority", r.getByPriority());
        section(b, "By status", r.getByStatus());
        section(b, "By category", r.getByCategory());

        if (!raisers.isEmpty()) {
            b.append("<tr><td style=\"padding:4px 28px 0;\"><div style=\"font-size:14px;font-weight:600;margin:14px 0 6px;\">Most tickets raised by</div>")
             .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"font-size:14px;\">");
            for (Object[] rr : raisers) b.append(row(name(rr), String.valueOf(((Number) rr[3]).longValue())));
            b.append("</table></td></tr>");
        }

        b.append("<tr><td style=\"padding:22px 28px 8px;\"><a href=\"").append(esc(mail.appLink("/tickets")))
         .append("\" style=\"display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;padding:10px 18px;border-radius:6px;font-size:14px;\">View your tickets</a></td></tr>")
         .append("<tr><td style=\"padding:16px 28px 24px;font-size:12px;color:#6b7280;border-top:1px solid #f0f1f3;\">")
         .append("You get this email because you are a Customer Admin of ").append(esc(company)).append(". ")
         .append("<a href=\"").append(esc(mail.appLink("/automation"))).append("\" style=\"color:#2563eb;\">Change or turn off this email</a>.")
         .append("</td></tr></table></td></tr></table></body></html>");
        return b.toString();
    }

    private static String tile(String label, String value, String sub) {
        return "<td width=\"33%\" style=\"padding:6px;\"><div style=\"background:#f9fafb;border:1px solid #eef0f3;border-radius:8px;padding:12px 8px;text-align:center;\">"
             + "<div style=\"font-size:12px;color:#6b7280;\">" + esc(label) + "</div>"
             + "<div style=\"font-size:26px;font-weight:600;margin:2px 0;\">" + esc(value) + "</div>"
             + "<div style=\"font-size:11px;color:#6b7280;min-height:14px;\">" + esc(sub) + "</div></div></td>";
    }

    private static String row(String label, String value) {
        return "<tr><td style=\"padding:5px 0;border-bottom:1px solid #f0f1f3;color:#374151;\">" + esc(label)
             + "</td><td align=\"right\" style=\"padding:5px 0;border-bottom:1px solid #f0f1f3;font-weight:600;\">" + esc(value) + "</td></tr>";
    }

    private static void section(StringBuilder b, String title, List<ReportResponse.Slice> list) {
        List<ReportResponse.Slice> nonZero = list.stream().filter(s -> s.count() > 0).toList();
        if (nonZero.isEmpty()) return;
        b.append("<tr><td style=\"padding:4px 28px 0;\"><div style=\"font-size:14px;font-weight:600;margin:14px 0 6px;\">").append(esc(title)).append("</div>")
         .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"font-size:14px;\">");
        for (ReportResponse.Slice s : nonZero) b.append(row(s.label(), String.valueOf(s.count())));
        b.append("</table></td></tr>");
    }

    // ── Formatting helpers ───────────────────────────────────────────────────

    private static String delta(long cur, long prev, String prevLabel) {
        long d = cur - prev;
        if (d == 0) return "same as " + prevLabel;
        return (d > 0 ? "▲ " + d + " more" : "▼ " + (-d) + " fewer") + " than " + prevLabel;
    }

    /** "—" rather than a misleading 0.0 when there was nothing to average. */
    private static String hours(double value, long basis) {
        return basis == 0 ? "—" : String.format(Locale.ENGLISH, "%.1f h", value);
    }

    private static String pct(int pct, long measured) {
        return measured == 0 ? "—" : pct + "% (" + measured + " ticket" + (measured == 1 ? "" : "s") + ")";
    }

    private static String name(Object[] row) {
        String first = row[1] == null ? "" : row[1].toString();
        String last = row[2] == null ? "" : row[2].toString();
        String n = (first + " " + last).trim();
        return n.isEmpty() ? "Unknown" : n;
    }

    private static String esc(String s) { return HtmlUtils.htmlEscape(s == null ? "" : s); }
}
