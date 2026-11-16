package com.konohahelpdesk.dto.automation;

import java.util.List;

/**
 * One company's automation settings, as the requesting user may use them.
 *
 * {@code featureEnabled} is the plan gate (AUTOMATION_RULES); {@code canEdit} is whether THIS user
 * may change the settings right now (staff: always; a Customer Admin: only while the company is
 * not locked). The two are separate so the page can say why a switch is disabled.
 */
public record AutomationSettingsResponse(
        Long companyId,
        String companyName,
        boolean featureEnabled,
        boolean canEdit,
        boolean customerCanEdit,
        boolean monthlySummaryEnabled,
        int monthlySummaryDay,
        List<String> monthlySummaryExtraRecipients,
        /** Email addresses of the Customer Admins who receive the summary today. */
        List<String> monthlySummaryAdminRecipients,
        /** Which month the next email will cover, e.g. "October 2026". */
        String nextSummaryPeriod,
        /** ISO-8601 with offset, in the automation zone. */
        String nextSummaryAt,
        String zone) {}
