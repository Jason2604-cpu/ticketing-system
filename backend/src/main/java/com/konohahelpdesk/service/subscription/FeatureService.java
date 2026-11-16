package com.konohahelpdesk.service.subscription;

import com.konohahelpdesk.dto.subscription.FeatureResponse;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.model.plan.Feature;
import com.konohahelpdesk.repository.plan.FeatureRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only feature catalog. Features are defined in app-config.properties and
 * seeded on startup — they are not created through the API.
 */
@Service
public class FeatureService {

    private final FeatureRepository featureRepository;

    public FeatureService(FeatureRepository featureRepository) {
        this.featureRepository = featureRepository;
    }

    public List<FeatureResponse> listAll() {
        return featureRepository.findAllByOrderByFeatureNameAsc().stream()
            .map(f -> FeatureResponse.of(f, null))
            .toList();
    }

    public List<Feature> allFeatures() {
        return featureRepository.findAllByOrderByFeatureNameAsc();
    }

    /** Normalises codes (trim, upper-case, de-dupe) and rejects any that don't exist. */
    public List<Feature> resolveCodes(Collection<String> codes) {
        Set<String> wanted = new LinkedHashSet<>();
        if (codes != null) {
            for (String c : codes) {
                if (c != null && !c.isBlank()) {
                    wanted.add(c.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        Map<String, Feature> byCode = allFeatures().stream()
            .collect(Collectors.toMap(Feature::getFeatureCode, Function.identity()));

        List<Feature> resolved = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String code : wanted) {
            Feature f = byCode.get(code);
            if (f == null) unknown.add(code); else resolved.add(f);
        }
        if (!unknown.isEmpty()) {
            throw new BusinessRuleException("Unknown feature code(s): " + String.join(", ", unknown));
        }
        return resolved;
    }
}
