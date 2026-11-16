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

    /**
     * {@code CompanySubscriptionService.hasFeature} in one round trip: an ACTIVE subscription that
     * either is PREDEFINED on an ACTIVE plan with the feature enabled, or is CUSTOM with the feature
     * enabled in the company's overrides. Returns 0 or 1.
     *
     * The old version loaded the subscription, its company and plan, then asked a second question —
     * four queries per call. The Knowledge Base type-ahead asks it on every pause in typing.
     */
    @Query("""
        select count(s) from CompanySubscription s
        left join s.plan p
        where s.company.id = :companyId
          and s.status = com.konohahelpdesk.model.plan.SubscriptionStatus.ACTIVE
          and (
                (s.subscriptionType = com.konohahelpdesk.model.plan.PlanType.PREDEFINED
                 and p.status = com.konohahelpdesk.model.plan.PlanStatus.ACTIVE
                 and exists (select 1 from PlanFeature pf
                             where pf.plan = p and pf.feature.featureCode = :code and pf.enabled = true))
             or (s.subscriptionType <> com.konohahelpdesk.model.plan.PlanType.PREDEFINED
                 and exists (select 1 from CompanySubscriptionFeatureOverride o
                             where o.company.id = :companyId and o.feature.featureCode = :code and o.enabled = true))
          )
        """)
    long countActiveFeature(@Param("companyId") Long companyId, @Param("code") String featureCode);

    boolean existsByPlanId(Long planId);
}
