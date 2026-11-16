package com.konohahelpdesk.repository.template;

import com.konohahelpdesk.model.template.TemplateKind;
import com.konohahelpdesk.model.template.TicketTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketTemplateRepository extends JpaRepository<TicketTemplate, Long> {

    /** The admin listing. company is fetch-joined because the response names it on every row. */
    @Query("""
        select t from TicketTemplate t
        left join fetch t.company
        order by t.kind asc, t.sortOrder asc, t.name asc
        """)
    List<TicketTemplate> findAllForListing();

    @Query("""
        select t from TicketTemplate t
        left join fetch t.company
        where t.id = :id
        """)
    Optional<TicketTemplate> findDetailById(@Param("id") Long id);

    /**
     * Templates offered in a picker: active, of the right kind, and either global or pinned to
     * this company. companyId is never null here — callers pass -1 for "staff, no company in
     * scope", which matches nothing and leaves only the global rows. That avoids the untyped-null
     * comparison Postgres rejects.
     */
    @Query("""
        select t from TicketTemplate t
        left join fetch t.company
        where t.active = true
          and t.kind = :kind
          and (t.company is null or t.company.id = :companyId)
        order by t.sortOrder asc, t.name asc
        """)
    List<TicketTemplate> findUsable(@Param("kind") TemplateKind kind, @Param("companyId") Long companyId);

    boolean existsByKindAndNameIgnoreCase(TemplateKind kind, String name);

    /**
     * Bumps the usage counter without loading the row or touching updatedAt — this fires on every
     * pick, and a template's "last edited" timestamp should mean edited, not used.
     */
    @Modifying
    @Query("update TicketTemplate t set t.useCount = t.useCount + 1 where t.id = :id")
    int incrementUseCount(@Param("id") Long id);
}
