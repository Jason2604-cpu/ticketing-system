package com.konohahelpdesk.repository.contract;

import com.konohahelpdesk.model.contract.ContractStatus;
import com.konohahelpdesk.model.contract.ServiceContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceContractRepository extends JpaRepository<ServiceContract, Long> {

    /**
     * The list view. company + owner are fetch-joined because the response names both for every
     * row — without this the page costs 2N extra queries with open-in-view off.
     * Filtering is done in the service against this one ordered load rather than as a dozen
     * derived queries: the contract count per provider is in the hundreds, not the millions.
     */
    @Query("""
        select distinct c from ServiceContract c
        left join fetch c.company
        left join fetch c.owner
        order by c.endDate asc, c.name asc
        """)
    List<ServiceContract> findAllForListing();

    @Query("""
        select c from ServiceContract c
        left join fetch c.company
        left join fetch c.owner
        where c.id = :id
        """)
    Optional<ServiceContract> findDetailById(@Param("id") Long id);

    List<ServiceContract> findByCompanyIdOrderByEndDateAsc(Long companyId);

    long countByCompanyId(Long companyId);

    /**
     * Candidates for the renewal-reminder sweep: still active, a reminder configured, and not
     * already sent for this end date. The date arithmetic stays in the service — Postgres can't
     * add an entity column's worth of days here without a native query.
     */
    @Query("""
        select c from ServiceContract c
        left join fetch c.company
        left join fetch c.owner
        where c.status = :status
          and c.renewalReminderDays is not null
          and (c.renewalReminderSentFor is null or c.renewalReminderSentFor <> c.endDate)
          and c.endDate >= :from
        """)
    List<ServiceContract> findRenewalCandidates(@Param("status") ContractStatus status, @Param("from") LocalDate from);
}
