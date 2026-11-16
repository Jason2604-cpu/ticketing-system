package com.konohahelpdesk.repository.company;

import com.konohahelpdesk.model.company.CompanyLimitConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyLimitConfigurationRepository extends JpaRepository<CompanyLimitConfiguration, Long> {

    Optional<CompanyLimitConfiguration> findByCompanyId(Long companyId);

    boolean existsByCompanyId(Long companyId);

    /** Batch form for the companies list — see CompanySubscriptionRepository.findWithPlanByCompanyIdIn. */
    List<CompanyLimitConfiguration> findByCompanyIdIn(Collection<Long> companyIds);
}
