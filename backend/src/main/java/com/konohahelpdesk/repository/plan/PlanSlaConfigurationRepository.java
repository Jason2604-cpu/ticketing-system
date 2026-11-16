package com.konohahelpdesk.repository.plan;

import com.konohahelpdesk.model.plan.PlanSlaConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PlanSlaConfigurationRepository extends JpaRepository<PlanSlaConfiguration, Long> {

    List<PlanSlaConfiguration> findByPlanId(Long planId);

    void deleteByPlanId(Long planId);
}
