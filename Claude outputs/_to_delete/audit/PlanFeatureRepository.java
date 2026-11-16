package com.konohahelpdesk.repository.plan;

import com.konohahelpdesk.model.plan.PlanFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlanFeatureRepository extends JpaRepository<PlanFeature, Long> {

    // All feature toggles configured for a predefined plan
    List<PlanFeature> findByPlanId(Long planId);
    boolean existsByPlanIdAndFeature_FeatureCodeAndEnabledTrue(Long planId, String featureCode);

    void deleteByPlanId(Long planId);
}
