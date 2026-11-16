package com.konohahelpdesk.service.automation;

import com.konohahelpdesk.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.*;

/**
 * Which clock automations are scheduled on (app.automation.zone, Asia/Kuala_Lumpur by default).
 *
 * Tickets store plain LocalDateTime in the server's zone, so a period decided in the automation
 * zone is converted to the server's before it reaches a query. With the JVM on the same zone (the
 * Dockerfile sets it) that conversion is the identity — it exists so the two settings can differ
 * without silently shifting every month boundary.
 */
@Component
public class AutomationClock {

    private final ZoneId zone;
    private final int sendHour;

    public AutomationClock(AppProperties props) {
        AppProperties.Automation cfg = props.getAutomation();
        this.zone = ZoneId.of(cfg.getZone());   // a bad zone fails startup, not the first sweep
        this.sendHour = Math.min(23, cfg.getSendHour());
    }

    public ZoneId zone() { return zone; }

    public int sendHour() { return sendHour; }

    public ZonedDateTime now() { return ZonedDateTime.now(zone); }

    /** The month that has just finished, as of {@code now}. */
    public YearMonth lastCompletedMonth(ZonedDateTime now) { return YearMonth.from(now).minusMonths(1); }

    /** When a company that chose {@code day} gets the summary for the month ending before {@code month}. */
    public ZonedDateTime dueAt(YearMonth month, int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth())).atTime(sendHour, 0).atZone(zone);
    }

    /** First instant of {@code month} in the automation zone, as the server-local value tickets are stored in. */
    public LocalDateTime startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(zone).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
