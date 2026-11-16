package com.konohahelpdesk.service.automation;

import com.konohahelpdesk.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Timer for the customer-facing automations. It only decides WHEN to look; whether a given company
 * is due is decided per company (plan feature, its own switch and day, and the run log).
 *
 * Hourly rather than a once-a-month cron so each company can pick its own day, and so a month
 * missed because the app was down is picked up by the next pass instead of lost.
 */
@Component
public class AutomationMonitor {

    private static final Logger log = LoggerFactory.getLogger(AutomationMonitor.class);

    private final MonthlySummarySweeper monthlySummary;
    private final AppProperties.Automation cfg;

    public AutomationMonitor(MonthlySummarySweeper monthlySummary, AppProperties props) {
        this.monthlySummary = monthlySummary;
        this.cfg = props.getAutomation();
    }

    @Scheduled(fixedDelayString = "${app.automation.check-interval-ms:3600000}",
               initialDelayString = "${app.automation.initial-delay-ms:180000}")
    public void sweep() {
        if (!cfg.isMonitorEnabled()) return;
        try {
            int sent = monthlySummary.runOnce();
            if (sent > 0) log.info("Automation sweep queued {} monthly summar{}", sent, sent == 1 ? "y" : "ies");
        } catch (RuntimeException e) {
            log.error("Automation sweep failed: {}", e.getMessage(), e);
        }
    }
}
