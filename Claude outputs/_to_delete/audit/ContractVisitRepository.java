package com.konohahelpdesk.repository.contract;

import com.konohahelpdesk.model.contract.ContractStatus;
import com.konohahelpdesk.model.contract.ContractVisit;
import com.konohahelpdesk.model.contract.VisitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractVisitRepository extends JpaRepository<ContractVisit, Long> {

    List<ContractVisit> findByContractIdOrderBySequenceAsc(Long contractId);

    /**
     * Batch form for the list page. The app polls every 15s, so loading each contract's visits
     * one query at a time would put N+1 round trips on a timer — this is one query for the lot,
     * grouped by contract in the service.
     */
    List<ContractVisit> findByContractIdInOrderBySequenceAsc(java.util.Collection<Long> contractIds);

    @Query("""
        select v from ContractVisit v
        join fetch v.contract c
        join fetch c.company
        where v.id = :id
        """)
    Optional<ContractVisit> findDetailById(@Param("id") Long id);

    /**
     * Visits whose ticket is due to be raised: still scheduled, no ticket yet, on a contract that
     * has auto-ticketing on and is still active.
     *
     * The lead time is per contract, so the cutoff can't be a single date in SQL — this fetches
     * everything scheduled up to the widest possible horizon and the sweep applies each
     * contract's own leadDays. Bounded by app.contracts.max-ticket-lead-days.
     */
    @Query("""
        select v from ContractVisit v
        join fetch v.contract c
        join fetch c.company
        where v.status = :scheduled
          and v.ticket is null
          and c.autoCreateTicket = true
          and c.status = :active
          and v.scheduledDate <= :horizon
        order by v.scheduledDate asc
        """)
    List<ContractVisit> findTicketCandidates(@Param("scheduled") VisitStatus scheduled,
                                             @Param("active") ContractStatus active,
                                             @Param("horizon") LocalDate horizon);

}
