package com.konohahelpdesk.repository.plan;

import com.konohahelpdesk.model.plan.Feature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FeatureRepository extends JpaRepository<Feature, Long> {

    Optional<Feature> findByFeatureCode(String featureCode);
    List<Feature> findAllByOrderByFeatureNameAsc();
}
