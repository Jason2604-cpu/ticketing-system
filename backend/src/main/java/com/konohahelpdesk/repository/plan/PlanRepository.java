package com.konohahelpdesk.repository.plan;

import com.konohahelpdesk.model.plan.Plan;
import com.konohahelpdesk.model.plan.PlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {
    Optional<Plan> findByPlanNameIgnoreCase(String planName);

    List<Plan> findByStatus(PlanStatus status);

    boolean existsByPlanNameIgnoreCase(String planName);

    boolean existsByPlanNameIgnoreCaseAndIdNot(String planName, Long id);
}
