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

    /** The admin listing. */
    List<TicketTemplate> findAllByOrderByKindAscSortOrderAscNameAsc();

    Optional<TicketTemplate> findById(Long id);

    /**
     * Every active template of one kind. Which of them a given company may use is decided in
     * {@code TemplateService} via {@link TicketTemplate#appliesTo}, not here.
     *
     * Filtering the many-to-many in JPQL would mean either an EXISTS subquery over the join table
     * or a join that has to be de-duplicated, and both are harder to read than the loop they
     * replace. Templates number in the dozens, not the millions, and the picker is cached for five
     * minutes client-side — this is the case where the simple thing is also the right one.
     */
    List<TicketTemplate> findByKindAndActiveTrueOrderBySortOrderAscNameAsc(TemplateKind kind);

    boolean existsByKindAndNameIgnoreCase(TemplateKind kind, String name);

    /**
     * Bumps the usage counter without loading the row or touching updatedAt — this fires on every
     * pick, and a template's "last edited" timestamp should mean edited, not used.
     */
    @Modifying
    @Query("update TicketTemplate t set t.useCount = t.useCount + 1 where t.id = :id")
    int incrementUseCount(@Param("id") Long id);
}
