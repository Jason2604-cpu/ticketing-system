package com.konohahelpdesk.repository.ticket;

import com.konohahelpdesk.model.ticket.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Aggregation queries behind /api/reports. Everything here is a GROUP BY / COUNT executed by the
 * database, so a report over 50,000 tickets costs the same round trip as one over 50 — unlike the
 * old client-side maths, which silently truncated at the ticket list's 500-row page cap.
 *
 * COMPANY SCOPING: every query takes an explicit {@code companyIds} list — the caller resolves
 * who may see what and hands over the exact set. That is what lets one query shape serve an admin
 * (every company), an engineer restricted to their assignments, and a customer pinned to their own.
 * ReportService guarantees the list is never empty, because SQL has no valid {@code IN ()}.
 *
 * A list rather than a nullable "all companies" parameter on purpose: `(:companyId is null or ...)`
 * makes Postgres reject the untyped null — the same trap that pushed TicketService onto Specifications.
 *
 * The date window is always half-open: {@code >= from} and {@code < to}.
 */
@Repository
public interface TicketReportRepository extends JpaRepository<Ticket, Long> {

    @Query("""
        select count(t) from Ticket t
        where t.company.id in :companyIds
          and t.createdAt >= :from and t.createdAt < :to
        """)
    long countCreated(@Param("companyIds") List<Long> companyIds,
                      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Tickets finished in the window. "Finished" is coalesce(resolvedAt, closedAt), matching
     * SlaCalculator — a ticket closed outright without ever being marked resolved is still done,
     * and used to be invisible to every count on this page.
     */
    @Query("""
        select count(t) from Ticket t
        where t.company.id in :companyIds
          and coalesce(t.resolvedAt, t.closedAt) >= :from and coalesce(t.resolvedAt, t.closedAt) < :to
        """)
    long countResolved(@Param("companyIds") List<Long> companyIds,
                       @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Status mix of tickets CREATED in the window (where they stand now). */
    @Query("""
        select t.status, count(t) from Ticket t
        where t.company.id in :companyIds
          and t.createdAt >= :from and t.createdAt < :to
        group by t.status
        """)
    List<Object[]> groupByStatus(@Param("companyIds") List<Long> companyIds,
                                 @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        select t.priority, count(t) from Ticket t
        where t.company.id in :companyIds
          and t.createdAt >= :from and t.createdAt < :to
        group by t.priority
        """)
    List<Object[]> groupByPriority(@Param("companyIds") List<Long> companyIds,
                                   @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        select t.category, count(t) from Ticket t
        where t.company.id in :companyIds
          and t.createdAt >= :from and t.createdAt < :to
        group by t.category
        """)
    List<Object[]> groupByCategory(@Param("companyIds") List<Long> companyIds,
                                   @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Tickets created per bucket. {@code fmt} is a Postgres to_char pattern —
     * 'YYYY-MM' for months, 'YYYY-MM-DD' for days — so one query serves both granularities.
     */
    @Query(value = """
        select to_char(created_at, cast(:fmt as text)) as bucket, count(*) as n
        from tickets
        where company_id in (:companyIds)
          and created_at >= :from and created_at < :to
        group by 1 order by 1
        """, nativeQuery = true)
    List<Object[]> createdSeries(@Param("companyIds") List<Long> companyIds, @Param("fmt") String fmt,
                                 @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = """
        select to_char(coalesce(resolved_at, closed_at), cast(:fmt as text)) as bucket, count(*) as n
        from tickets
        where company_id in (:companyIds)
          and coalesce(resolved_at, closed_at) >= :from and coalesce(resolved_at, closed_at) < :to
        group by 1 order by 1
        """, nativeQuery = true)
    List<Object[]> resolvedSeries(@Param("companyIds") List<Long> companyIds, @Param("fmt") String fmt,
                                  @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Raw timestamps for every ticket created in the window:
     * [companyId, assigneeId, createdAt, firstResponseAt, stoppedAt].
     *
     * THIS REPLACES THE SQL AVERAGES, and the reason is the mismatch that made the page look
     * broken. Every SLA deadline in this app is stamped in WORKING minutes through BusinessCalendar
     * — a ticket raised Friday 17:00 and answered Monday 09:00 has used one working hour and
     * comfortably meets a 4-hour response target. The old averages were plain
     * {@code extract(epoch from ...)}, so that same ticket reported as 64 hours. The page ended up
     * showing "average first response 64h" directly beside "response compliance 100%".
     *
     * Business hours live in Java (per-company schedules, cached), not in SQL, and reimplementing
     * them as a Postgres function would be a second source of truth for the thing this whole fix
     * is about. So the rows come back raw and ReportService does the arithmetic through the same
     * BusinessCalendar that stamped the deadlines.
     *
     * This is the one query on the page that returns rows rather than aggregates. It is bounded by
     * the window and selects five scalar columns with no joins, so it stays cheap — but it is the
     * reason ReportService caps the range at three years.
     */
    @Query(value = """
        select company_id, assignee_id, created_at, first_response_at, coalesce(resolved_at, closed_at)
        from tickets
        where company_id in (:companyIds)
          and created_at >= :from and created_at < :to
        """, nativeQuery = true)
    List<Object[]> durationRows(@Param("companyIds") List<Long> companyIds,
                                @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * SLA outcome counts for the window, as a single row:
     * [resolutionMet, resolutionMissed, responseMet, responseMissed, measured, total].
     *
     * TWO THINGS HERE USED TO DISAGREE WITH THE REST OF THE APP, and both are fixed:
     *
     *  1. THE WINDOW. This used to filter on resolved_at / first_response_at while every other
     *     query on the page filters on created_at, so the SLA block measured a different set of
     *     tickets from the totals sitting next to it — "40 tickets, 92% compliance" where the 92%
     *     was not about those 40. It now bounds on created_at like everything else, so the numbers
     *     reconcile: `total` is the same figure as Totals.created, and `measured` says how many of
     *     those have a stopped clock and a target to judge it against.
     *
     *  2. THE STOP TIME. SlaCalculator treats a ticket closed without ever being marked resolved as
     *     finished at closed_at; this only ever looked at resolved_at, so a staff member closing an
     *     open ticket outright produced a ticket the UI showed as MET or BREACHED and the report
     *     counted as neither. coalesce(resolved_at, closed_at) is now the single definition on both
     *     sides.
     *
     * Only tickets that actually have a target are counted — a plan with no SLA for a priority must
     * not drag compliance down.
     */
    @Query(value = """
        select
          count(*) filter (where coalesce(resolved_at, closed_at) is not null and resolution_due_at is not null
                             and coalesce(resolved_at, closed_at) <= resolution_due_at),
          count(*) filter (where coalesce(resolved_at, closed_at) is not null and resolution_due_at is not null
                             and coalesce(resolved_at, closed_at) >  resolution_due_at),
          count(*) filter (where first_response_at is not null and response_due_at is not null
                             and first_response_at <= response_due_at),
          count(*) filter (where first_response_at is not null and response_due_at is not null
                             and first_response_at >  response_due_at),
          count(*) filter (where coalesce(resolved_at, closed_at) is not null and resolution_due_at is not null),
          count(*)
        from tickets
        where company_id in (:companyIds)
          and created_at >= :from and created_at < :to
        """, nativeQuery = true)
    List<Object[]> slaCounts(@Param("companyIds") List<Long> companyIds,
                             @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Live count — tickets still open right now with at least one clock in SlaCalculator's BREACHED
     * state. Deliberately ignores the report's date window: it answers "what is late right now",
     * which is not a property of any past period, and ReportService labels it that way.
     *
     * The second response clause is the fix for a mismatch with the UI: a ticket that DID get its
     * first response, but late, is BREACHED on SlaCalculator's response clock forever after. The
     * old query only looked for tickets with no response at all, so such a ticket showed a red SLA
     * badge on its detail page while the report insisted nothing was breaching.
     *
     * Counts TICKETS, not clocks — a ticket that blew both deadlines is one row here, but two in
     * the met/missed columns above. The two numbers are in different units on purpose.
     */
    @Query("""
        select count(t) from Ticket t
        where t.company.id in :companyIds
          and t.status in (com.konohahelpdesk.model.ticket.TicketStatus.OPEN,
                           com.konohahelpdesk.model.ticket.TicketStatus.IN_PROGRESS)
          and (
                (t.responseDueAt   is not null and t.firstResponseAt is null     and t.responseDueAt < :now)
             or (t.responseDueAt   is not null and t.firstResponseAt is not null and t.firstResponseAt > t.responseDueAt)
             or (t.resolutionDueAt is not null and t.resolutionDueAt < :now)
          )
        """)
    long countCurrentlyBreaching(@Param("companyIds") List<Long> companyIds, @Param("now") LocalDateTime now);

    /**
     * Per-company rollup: [companyId, name, created, resolvedInWindow, slaMet, slaTotal].
     *
     * The slaMet/slaTotal filters now use the same coalesce(resolved_at, closed_at) definition as
     * slaCounts, over the same created_at-bounded rows — so a company's compliance column and the
     * headline compliance figure are finally computed on the same population and reconcile.
     * (Previously the outer query bounded on created_at while these two filters carried no date
     * bound of their own, so the column silently mixed in resolutions from any time ever.)
     */
    @Query(value = """
        select c.id, c.name,
               count(*),
               count(*) filter (where t.resolved_at is not null and t.resolved_at >= :from and t.resolved_at < :to),
               count(*) filter (where coalesce(t.resolved_at, t.closed_at) is not null and t.resolution_due_at is not null
                                  and coalesce(t.resolved_at, t.closed_at) <= t.resolution_due_at),
               count(*) filter (where coalesce(t.resolved_at, t.closed_at) is not null and t.resolution_due_at is not null)
        from tickets t join companies c on c.id = t.company_id
        where t.company_id in (:companyIds)
          and t.created_at >= :from and t.created_at < :to
        group by c.id, c.name
        order by 3 desc
        """, nativeQuery = true)
    List<Object[]> groupByCompany(@Param("companyIds") List<Long> companyIds,
                                  @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Per-engineer rollup: [userId, first, last, assigned, resolved].
     * The average resolution time is NOT here — ReportService computes it in working hours from
     * {@link #durationRows}, for the same reason the headline averages moved.
     */
    @Query(value = """
        select u.id, u.first_name, u.last_name,
               count(*),
               count(*) filter (where coalesce(t.resolved_at, t.closed_at) is not null)
        from tickets t join users u on u.id = t.assignee_id
        where t.company_id in (:companyIds)
          and t.created_at >= :from and t.created_at < :to
        group by u.id, u.first_name, u.last_name
        order by 4 desc
        """, nativeQuery = true)
    List<Object[]> groupByEngineer(@Param("companyIds") List<Long> companyIds,
                                   @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
