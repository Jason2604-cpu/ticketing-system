package com.konohahelpdesk.controller.audit;

import com.konohahelpdesk.dto.audit.AuditEventResponse;
import com.konohahelpdesk.dto.audit.AuditPageResponse;
import com.konohahelpdesk.model.audit.AuditLevel;
import com.konohahelpdesk.service.audit.AuditQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * The audit trail: sign-ins, permission changes, record edits, rate-limit rejections.
 *
 * ADMIN or the VIEW_AUDIT_LOG privilege, and READ ONLY — there is deliberately no write or delete
 * endpoint anywhere on this controller. An audit log an admin can edit is not an audit log;
 * trimming happens only through the configured retention window.
 *
 * NOTE: this is the provider-internal trail and spans every company, so it is not the customer
 * AUDIT_LOGS plan feature. That would be a separate, company-scoped view of the events touching
 * one customer's own tickets and users.
 */
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('PRIV_VIEW_AUDIT_LOG')")
public class AuditController {

    private final AuditQueryService audit;

    public AuditController(AuditQueryService audit) {
        this.audit = audit;
    }

    @GetMapping
    public ResponseEntity<AuditPageResponse> list(
        @RequestParam(required = false) String action,
        @RequestParam(required = false) Long actorId,
        @RequestParam(required = false) AuditLevel level,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) String q,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(audit.page(new AuditQueryService.Filters(action, actorId, level, from, to, q), page, size));
    }

    /** The same filtered rows as a spreadsheet-friendly CSV, capped by app.audit.max-export-rows. */
    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(
        @RequestParam(required = false) String action,
        @RequestParam(required = false) Long actorId,
        @RequestParam(required = false) AuditLevel level,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) String q
    ) {
        List<AuditEventResponse> rows = audit.forExport(new AuditQueryService.Filters(action, actorId, level, from, to, q));
        String filename = "audit-log_" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(AuditCsvWriter.write(rows));
    }
}
