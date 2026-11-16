package com.konohahelpdesk.controller.automation;

import com.konohahelpdesk.dto.automation.*;
import com.konohahelpdesk.security.CurrentUser;
import com.konohahelpdesk.service.automation.AutomationSettingsService;
import com.konohahelpdesk.service.automation.MonthlySummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Automation settings per company (AUTOMATION_RULES).
 *
 * Role and plan checks live in {@link AutomationSettingsService#requireView} so the same rules
 * apply to every endpoint here: staff see the companies they can see, a Customer Admin only their
 * own and only when the plan includes the feature. Writes are further limited by the company's lock.
 */
@RestController
@RequestMapping("/api/automation")
@PreAuthorize("hasAnyRole('ADMIN','ENGINEER','CUSTOMER_ADMIN')")
public class AutomationController {

    private final AutomationSettingsService settings;
    private final MonthlySummaryService summary;
    private final CurrentUser currentUser;

    public AutomationController(AutomationSettingsService settings, MonthlySummaryService summary, CurrentUser currentUser) {
        this.settings = settings;
        this.summary = summary;
        this.currentUser = currentUser;
    }

    @GetMapping("/companies/{companyId}/settings")
    public ResponseEntity<AutomationSettingsResponse> get(@PathVariable Long companyId) {
        return ResponseEntity.ok(settings.view(currentUser.get(), companyId));
    }

    @PutMapping("/companies/{companyId}/settings")
    public ResponseEntity<AutomationSettingsResponse> update(@PathVariable Long companyId,
                                                             @RequestBody UpdateAutomationSettingsRequest body) {
        return ResponseEntity.ok(settings.update(currentUser.get(), companyId, body));
    }

    @GetMapping("/companies/{companyId}/runs")
    public ResponseEntity<List<AutomationRunResponse>> runs(@PathVariable Long companyId) {
        return ResponseEntity.ok(settings.recentRuns(currentUser.get(), companyId));
    }

    @GetMapping("/companies/{companyId}/monthly-summary/preview")
    public ResponseEntity<MonthlySummaryPreview> preview(@PathVariable Long companyId) {
        return ResponseEntity.ok(summary.preview(currentUser.get(), companyId));
    }

    @PostMapping("/companies/{companyId}/monthly-summary/send-test")
    public ResponseEntity<Map<String, Boolean>> sendTest(@PathVariable Long companyId) {
        summary.sendTest(currentUser.get(), companyId);
        return ResponseEntity.ok(Map.of("queued", true));
    }
}
