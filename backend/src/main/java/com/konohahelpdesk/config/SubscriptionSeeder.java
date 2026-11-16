package com.konohahelpdesk.config;

import com.konohahelpdesk.dto.subscription.LimitsDto;
import com.konohahelpdesk.dto.subscription.PlanRequest;
import com.konohahelpdesk.dto.subscription.SlaDto;
import com.konohahelpdesk.model.plan.Feature;
import com.konohahelpdesk.model.plan.Plan;
import com.konohahelpdesk.model.plan.SlaPriority;
import com.konohahelpdesk.repository.plan.FeatureRepository;
import com.konohahelpdesk.repository.plan.PlanRepository;
import com.konohahelpdesk.service.subscription.PlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Creates the feature catalog and predefined plans declared in app-config.properties
 * (app.subscription.features / app.subscription.plans) on startup.
 *
 * Features are always upserted (names follow the config). Plans are created if missing;
 * existing plans are left untouched unless app.subscription.overwrite-seeded-plans=true.
 */
@Component
public class SubscriptionSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionSeeder.class);

    private final AppProperties.Subscription cfg;
    private final FeatureRepository featureRepository;
    private final PlanRepository planRepository;
    private final PlanService planService;

    public SubscriptionSeeder(AppProperties props, FeatureRepository featureRepository,
                              PlanRepository planRepository, PlanService planService) {
        this.cfg = props.getSubscription();
        this.featureRepository = featureRepository;
        this.planRepository = planRepository;
        this.planService = planService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!cfg.isSeedOnStartup()) {
            log.info("Subscription seeding disabled (app.subscription.seed-on-startup=false)");
            return;
        }
        seedFeatures();
        seedPlans();
    }

    private void seedFeatures() {
        int created = 0;
        for (AppProperties.FeatureSeed seed : cfg.getFeatures()) {
            String code = seed.getCode().trim().toUpperCase(Locale.ROOT);
            Optional<Feature> existing = featureRepository.findByFeatureCode(code);
            if (existing.isPresent()) {
                Feature f = existing.get();
                if (!seed.getName().equals(f.getFeatureName())) {
                    f.setFeatureName(seed.getName());
                    featureRepository.save(f);
                }
            } else {
                Feature f = new Feature();
                f.setFeatureCode(code);
                f.setFeatureName(seed.getName());
                featureRepository.save(f);
                created++;
            }
        }
        featureRepository.flush();
        log.info("Feature catalog: {} configured, {} created", cfg.getFeatures().size(), created);
    }

    private void seedPlans() {
        List<String> allCodes = cfg.getFeatures().stream()
            .map(f -> f.getCode().trim().toUpperCase(Locale.ROOT)).toList();

        for (AppProperties.PlanSeed seed : cfg.getPlans()) {
            PlanRequest req = toRequest(seed, allCodes);
            Optional<Plan> existing = planRepository.findByPlanNameIgnoreCase(seed.getName().trim());
            if (existing.isEmpty()) {
                planService.create(req);
                log.info("Seeded plan '{}'", seed.getName());
            } else if (cfg.isOverwriteSeededPlans()) {
                planService.update(existing.get().getId(), req);
                log.info("Refreshed plan '{}' from config", seed.getName());
            }
        }
    }

    private PlanRequest toRequest(AppProperties.PlanSeed seed, List<String> allCodes) {
        PlanRequest req = new PlanRequest();
        req.setPlanName(seed.getName().trim());
        req.setDescription(seed.getDescription());

        List<String> codes = new ArrayList<>();
        for (String c : seed.getFeatures()) {
            if ("*".equals(c.trim())) { codes.addAll(allCodes); } else { codes.add(c.trim()); }
        }
        req.setFeatureCodes(codes);

        AppProperties.Limits l = seed.getLimits();
        req.setLimits(new LimitsDto(l.getMaxUsers(), l.getMonthlyTicketLimit(), l.getStorageLimitMb()));

        List<SlaDto> sla = new ArrayList<>();
        for (Map.Entry<SlaPriority, AppProperties.Sla> e : seed.getSla().entrySet()) {
            sla.add(new SlaDto(e.getKey(), e.getValue().getResponseMinutes(), e.getValue().getResolutionMinutes()));
        }
        req.setSla(sla);
        return req;
    }
}
