package com.konohahelpdesk.repository.company;

import com.konohahelpdesk.model.company.CompanySlaConfiguration;
import com.konohahelpdesk.model.plan.SlaPriority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompanySlaConfigurationRepository extends JpaRepository<CompanySlaConfiguration, Long> {

    List<CompanySlaConfiguration> findByCompanyId(Long companyId);
    void deleteByCompanyId(Long companyId);
}
