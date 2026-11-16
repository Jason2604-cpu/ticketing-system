package com.konohahelpdesk.repository.company;

import com.konohahelpdesk.model.company.CompanySubscriptionFeatureOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompanySubscriptionFeatureOverrideRepository extends JpaRepository<CompanySubscriptionFeatureOverride, Long> {

    List<CompanySubscriptionFeatureOverride> findByCompanyId(Long companyId);
    boolean existsByCompanyIdAndFeature_FeatureCodeAndEnabledTrue(Long companyId, String featureCode);

    void deleteByCompanyId(Long companyId);
}
