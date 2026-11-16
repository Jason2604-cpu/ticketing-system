package com.konohahelpdesk.repository.ticket;

import com.konohahelpdesk.model.ticket.Ticket;
import com.konohahelpdesk.model.ticket.TicketStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {
    long countByCompanyIdAndCreatedAtGreaterThanEqual(Long companyId, LocalDateTime since);

    /** Tickets in any of the given statuses right now (the monthly summary's "still open" figure). */
    long countByCompanyIdAndStatusIn(Long companyId, java.util.Collection<TicketStatus> statuses);

    /**
     * Tickets whose clocks are still running and that have blown a deadline the sweep
     * hasn't recorded yet. Ordered oldest-breach-first so a backlog drains fairly.
     *
     * The response clock only counts while there has been no first reply; the resolution
     * clock counts until the ticket leaves OPEN/IN_PROGRESS (the `statuses` argument).
     */
    @Query("""
        select t from Ticket t
        where t.status in :statuses
          and (
                (t.responseDueAt   is not null and t.firstResponseAt is null
                   and t.responseDueAt   < :now and t.responseBreachedAt   is null)
             or (t.resolutionDueAt is not null
                   and t.resolutionDueAt < :now and t.resolutionBreachedAt is null)
          )
        order by t.responseDueAt asc nulls last, t.resolutionDueAt asc nulls last
        """)
    List<Ticket> findSlaBreachCandidates(@Param("statuses") List<TicketStatus> statuses,
                                         @Param("now") LocalDateTime now,
                                         Pageable pageable);

    /**
     * Open-ticket counts per company: [companyId, count]. Scoped by the caller's company list.
     *
     * The sidebar needs two numbers — a total open count and a per-company count for the five
     * companies it lists — and used to get them by downloading the entire ticket list (up to the
     * 500-row cap, each row a full summary DTO with SLA state and comment counts) every 15
     * seconds, on every page, for every signed-in staff member. This is the same information in
     * one grouped query and a few hundred bytes.
     */
    @Query("""
        select t.company.id, count(t) from Ticket t
        where t.company.id in :companyIds
          and t.status in (com.konohahelpdesk.model.ticket.TicketStatus.OPEN,
                           com.konohahelpdesk.model.ticket.TicketStatus.IN_PROGRESS)
        group by t.company.id
        """)
    List<Object[]> countOpenByCompany(@Param("companyIds") java.util.Collection<Long> companyIds);

    /** Tickets still being worked (OPEN / IN_PROGRESS) that are assigned to this user. */
    @Query("""
        select t from Ticket t
        where t.assignee.id = :userId
          and t.status in (com.konohahelpdesk.model.ticket.TicketStatus.OPEN,
                           com.konohahelpdesk.model.ticket.TicketStatus.IN_PROGRESS)
        """)
    List<Ticket> findActiveByAssigneeId(@Param("userId") Long userId);

    /** Same, but for every company — the admin case, where no scope restriction applies. */
    @Query("""
        select t.company.id, count(t) from Ticket t
        where t.status in (com.konohahelpdesk.model.ticket.TicketStatus.OPEN,
                           com.konohahelpdesk.model.ticket.TicketStatus.IN_PROGRESS)
        group by t.company.id
        """)
    List<Object[]> countOpenByCompanyAll();
}
