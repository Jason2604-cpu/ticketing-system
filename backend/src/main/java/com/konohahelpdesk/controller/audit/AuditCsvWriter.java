package com.konohahelpdesk.controller.audit;

import com.konohahelpdesk.dto.audit.AuditEventResponse;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** Flat CSV of the audit trail, same conventions as {@code ReportCsvWriter}. */
final class AuditCsvWriter {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AuditCsvWriter() {}

    static String write(List<AuditEventResponse> events) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // BOM, so Excel reads it as UTF-8 rather than the local codepage
        line(sb, "Timestamp", "Level", "Action", "Actor", "Actor email", "Actor role", "IP", "Details");
        for (AuditEventResponse e : events) {
            line(sb,
                e.getCreatedAt() == null ? "" : e.getCreatedAt().format(TS),
                e.getLevel() == null ? "" : e.getLevel().name(),
                e.getAction(),
                e.getActorName(),
                e.getActorEmail(),
                e.getActorRole(),
                e.getIpAddress(),
                e.getDetails());
        }
        return sb.toString();
    }

    private static void line(StringBuilder sb, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(cells[i]));
        }
        sb.append('\n');
    }

    /**
     * RFC 4180 quoting, plus a leading apostrophe on anything starting with = + - @. Audit details
     * are partly user-supplied (an email address, a company name), so a value like "=cmd|..." has
     * to be shown as text rather than run as a formula when the file is opened.
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
