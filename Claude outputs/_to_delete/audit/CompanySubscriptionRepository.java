package com.konohahelpdesk.repository.company;

import com.konohahelpdesk.model.company.CompanySubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompanySubscriptionRepository extends JpaRepository<CompanySubscription, Long> {

    // A company has at most one subscription row (enforced by unique company_id)
    Optional<CompanySubscription> findByCompanyId(Long companyId);

    /**
     * Every subscription for a set of companies, with the plan already joined.
     *
     * Exists so the companies list can resolve N companies in one round trip instead of calling
     * getResolved() per row — that path costs about nine queries each, one of them the storage-sum
     * with three IN-subqueries, and the list refetches every 15 seconds.
     */
    @Query("""
        select s from CompanySubscription s
        left join fetch s.plan
        where s.company.id in :companyIds
        """)
    List<CompanySubscription> findWithPlanByCompanyIdIn(@Param("companyIds") Collection<Long> companyIds);

    boolean existsByCompanyId(Long companyId);

    boolean existsByPlanId(Long planId);
}
