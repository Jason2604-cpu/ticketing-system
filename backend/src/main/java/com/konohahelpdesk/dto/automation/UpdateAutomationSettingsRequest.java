package com.konohahelpdesk.dto.automation;

import java.util.List;

/** Every field is optional: null means "leave as it is". */
public record UpdateAutomationSettingsRequest(
        Boolean monthlySummaryEnabled,
        Integer monthlySummaryDay,
        List<String> monthlySummaryExtraRecipients,
        /** Staff only: lock this company's automations so its Customer Admins can't change them. */
        Boolean customerCanEdit) {}
