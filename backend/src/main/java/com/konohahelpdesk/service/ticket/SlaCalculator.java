package com.konohahelpdesk.service.ticket;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.service.company.BusinessCalendar;
import com.konohahelpdesk.model.ticket.SlaState;
import com.konohahelpdesk.model.ticket.Ticket;
import com.konohahelpdesk.model.ticket.TicketStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Turns a ticket's due dates plus what actually happened into an {@link SlaState}.
 *
 * Single source of truth for "is this ticket late" on the Java side: the API responses and the
 * breach sweep both read the answer from here.
 *
 * The reports do NOT call this class — they aggregate in SQL, because loading every ticket to ask
 * this method would reintroduce the row cap that TicketReportRepository exists to remove. That
 * means the definitions below are mirrored by hand in TicketReportRepository's SLA queries, and
 * the two drifting apart is exactly what made the analytics page disagree with the ticket badges.
 * IF YOU CHANGE A RULE HERE, CHANGE IT THERE TOO. The four rules that have to stay in step:
 *   - the clock stops at coalesce(resolvedAt, closedAt), not resolvedAt alone;
 *   - finishing exactly on the deadline is MET, not missed (<=, never <);
 *   - a first response that arrived late leaves the response clock BREACHED for good;
 *   - an ON_HOLD ticket's RESOLUTION clock is read at slaPausedAt instead of now (in SQL:
 *     coalesce(sla_paused_at, :now)) — missed if the deadline had already gone when the hold
 *     started, otherwise PAUSED / unscored. The response clock is never paused.
 */
@Component
public class SlaCalculator {

    private final AppProperties.SlaMonitoring cfg;
    private final BusinessCalendar calendar;

    public SlaCalculator(AppProperties props, BusinessCalendar calendar) {
        this.cfg = props.getSla();
        this.calendar = calendar;
    }

    /** First-response clock: stops the moment a staff member replies publicly. */
    public SlaState responseState(Ticket t) {
        // The response clock stops at the first public staff reply — or, when there never was
        // one, at the moment the ticket was resolved/closed. Without that second stop a ticket
        // fixed inside its window but without a written reply turned BREACHED later on.
        LocalDateTime stopped = t.getFirstResponseAt() != null ? t.getFirstResponseAt() : finishedAt(t);
        return state(companyId(t), slaStart(t), t.getResponseDueAt(), stopped, t.getResponseBreachedAt());
    }

    public SlaState resolutionState(Ticket t) {
        if (t.getStatus() == TicketStatus.ON_HOLD && t.getResolutionDueAt() != null) {
            // Paused: the clock is read at the moment it stopped. A deadline that had already gone
            // by then was missed before the hold, and a hold doesn't take that back.
            LocalDateTime pausedAt = t.getSlaPausedAt();
            boolean missedBeforeHold = t.getResolutionBreachedAt() != null
                || (pausedAt != null && pausedAt.isAfter(t.getResolutionDueAt()));
            return missedBeforeHold ? SlaState.BREACHED : SlaState.PAUSED;
        }
        return state(companyId(t), slaStart(t), t.getResolutionDueAt(), finishedAt(t), t.getResolutionBreachedAt());
    }

    /** When the ticket was resolved (or closed without being resolved first); null while it is open. */
    private static LocalDateTime finishedAt(Ticket t) {
        return t.getResolvedAt() != null ? t.getResolvedAt() : t.getClosedAt();
    }

    /**
     * Start of the SLA clock the current deadlines were stamped from: creation, or the latest
     * reopen. Measuring "how much of the window is used" from createdAt made a reopened ticket
     * read AT_RISK the moment it was reopened.
     */
    public static LocalDateTime slaStart(Ticket t) {
        return t.getSlaStartedAt() != null ? t.getSlaStartedAt() : t.getCreatedAt();
    }

    private static Long companyId(Ticket t) {
        return t.getCompany() == null ? null : t.getCompany().getId();
    }

    /**
     * @param start     when the clock started (ticket creation)
     * @param due       the deadline, or null when the plan sets no target
     * @param stoppedAt when the target was actually hit, or null if it hasn't been
     * @param breachedAt the persisted breach stamp, if the sweep already recorded one
     */
    private SlaState state(Long companyId, LocalDateTime start, LocalDateTime due,
                           LocalDateTime stoppedAt, LocalDateTime breachedAt) {
        if (due == null) return SlaState.NONE;
        if (stoppedAt != null) return stoppedAt.isAfter(due) ? SlaState.BREACHED : SlaState.MET;
        if (breachedAt != null) return SlaState.BREACHED;

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(due)) return SlaState.BREACHED;   // still open and past the deadline
        if (start == null) return SlaState.ON_TRACK;

        // Measured in WORKING minutes, matching how the deadline was stamped. On the wall clock a
        // ticket raised on Friday evening would read "at risk" all weekend while its clock is paused.
        long total = calendar.workingMinutesBetween(companyId, start, due);
        if (total <= 0) return SlaState.ON_TRACK;
        long elapsed = calendar.workingMinutesBetween(companyId, start, now);
        return (elapsed * 100L / total) >= cfg.getAtRiskPercent() ? SlaState.AT_RISK : SlaState.ON_TRACK;
    }

    /**
     * True when the ticket's RESOLUTION clock is running: OPEN or IN_PROGRESS. A closed ticket can't
     * newly breach, and neither can one ON_HOLD (its clock is paused) — see {@link #isOpenWork}.
     */
    public static boolean isActive(Ticket t) {
        return t.getStatus() == TicketStatus.OPEN || t.getStatus() == TicketStatus.IN_PROGRESS;
    }

    /**
     * Unfinished work: OPEN, IN_PROGRESS or ON_HOLD. What counts toward open-ticket badges and
     * workloads, and what can still be assigned — a ticket waiting on a spare part is still somebody's.
     */
    public static boolean isOpenWork(Ticket t) {
        return isActive(t) || t.getStatus() == TicketStatus.ON_HOLD;
    }

    /** "Now" for the resolution clock: frozen at the start of the hold while the ticket is on hold. */
    public static LocalDateTime resolutionClockNow(Ticket t, LocalDateTime now) {
        return t.getStatus() == TicketStatus.ON_HOLD && t.getSlaPausedAt() != null ? t.getSlaPausedAt() : now;
    }
}
