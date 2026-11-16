package com.konohahelpdesk.repository.automation;

import com.konohahelpdesk.model.automation.AutomationRun;
import com.konohahelpdesk.model.automation.AutomationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AutomationRunRepository extends JpaRepository<AutomationRun, Long> {

    Optional<AutomationRun> findByCompanyIdAndAutomationTypeAndPeriodKey(Long companyId, AutomationType type, String periodKey);

    List<AutomationRun> findByCompanyIdOrderByRanAtDesc(Long companyId, Pageable page);
}
