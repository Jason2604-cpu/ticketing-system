package com.konohahelpdesk.dto.automation;

import java.util.List;

/** The rendered email for the last completed month, plus who the real one would go to. */
public record MonthlySummaryPreview(String subject, String html, String period, List<String> recipients) {}
