package com.konohahelpdesk.dto.automation;

import com.konohahelpdesk.model.automation.AutomationRun;

import java.time.LocalDateTime;

public record AutomationRunResponse(Long id, String type, String period, String status,
                                    int recipients, int attempts, String detail, LocalDateTime ranAt) {
    public static AutomationRunResponse of(AutomationRun r) {
        return new AutomationRunResponse(r.getId(), r.getAutomationType().name(), r.getPeriodKey(),
            r.getStatus().name(), r.getRecipientCount(), r.getAttempts(), r.getDetail(), r.getRanAt());
    }
}
