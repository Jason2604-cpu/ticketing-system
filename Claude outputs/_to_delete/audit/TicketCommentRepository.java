package com.konohahelpdesk.repository.ticket;

import com.konohahelpdesk.model.ticket.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {
    List<TicketComment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
    List<TicketComment> findByTicketIdAndInternalFalseOrderByCreatedAtAsc(Long ticketId);
    long countByTicketIdAndInternalFalseAndSystemFalse(Long ticketId);

    /**
     * Public comment counts for a whole page of tickets: [ticketId, count].
     *
     * The ticket list used to call the single-id count above once per row. At the 500-row list cap
     * that was up to 500 extra queries per request, on a list the sidebar and the tickets page both
     * refetch every 15 seconds — comfortably the most expensive thing the app did.
     *
     * Tickets with no public comments simply don't appear in the result; the caller defaults to 0.
     */
    @Query("""
        select c.ticket.id, count(c) from TicketComment c
        where c.ticket.id in :ticketIds and c.internal = false and c.system = false
        group by c.ticket.id
        """)
    List<Object[]> countPublicByTicketIds(@Param("ticketIds") Collection<Long> ticketIds);
}
