package com.konohahelpdesk.repository.automation;

import com.konohahelpdesk.model.automation.AutomationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AutomationSettingsRepository extends JpaRepository<AutomationSettings, Long> {
}
