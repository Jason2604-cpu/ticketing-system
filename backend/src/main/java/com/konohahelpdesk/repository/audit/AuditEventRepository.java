package com.konohahelpdesk.repository.audit;

import com.konohahelpdesk.model.audit.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Filtering goes through {@link JpaSpecificationExecutor}, not derived queries with nullable
 * params: a JPQL {@code (:x is null or col = :x)} sends an untyped null that Postgres refuses.
 * Same reason TicketService and ReportService avoid the pattern.
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {

    /** The distinct action names actually present, for the viewer's filter dropdown. */
    @Query("select distinct a.action from AuditEvent a order by a.action asc")
    List<String> findDistinctActions();

    @Modifying
    @Query("delete from AuditEvent a where a.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
