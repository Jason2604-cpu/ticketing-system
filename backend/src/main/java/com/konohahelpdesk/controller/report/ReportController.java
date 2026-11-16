package com.konohahelpdesk.controller.report;

import com.konohahelpdesk.dto.report.ReportResponse;
import com.konohahelpdesk.security.CurrentUser;
import com.konohahelpdesk.service.report.ReportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Ticket reporting over a date range.
 *
 * Access is decided inside {@link ReportService#build}: staff see any company (or all of them),
 * a customer only their own and only when their plan carries the REPORTING feature — so a
 * Starter customer typing the URL gets a 403 rather than a page of someone else's numbers.
 */
@RestController
@RequestMapping("/api/reports")
// Not for the Default employee role (STAFF): tickets and reports are engineer/admin/customer work,
// and the service code scopes everything by those roles. Method-level @PreAuthorize below overrides.
@PreAuthorize("hasAnyRole('ADMIN','ENGINEER','CUSTOMER_ADMIN','CUSTOMER')")
public class ReportController {

    private final ReportService reports;
    private final CurrentUser currentUser;

    public ReportController(ReportService reports, CurrentUser currentUser) {
        this.reports = reports;
        this.currentUser = currentUser;
    }

    /** @param companyId omit for "all companies" (staff only); customers are pinned to their own. */
    @GetMapping
    public ResponseEntity<ReportResponse> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long companyId) {
        return ResponseEntity.ok(reports.build(currentUser.get(), from, to, companyId));
    }

    /** The same report as a spreadsheet-friendly CSV. */
    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long companyId) {
        ReportResponse r = reports.build(currentUser.get(), from, to, companyId);
        String filename = "ticket-report_" + r.getFrom() + "_to_" + r.getTo() + ".csv";
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(ReportCsvWriter.write(r));
    }
}
