package com.konohahelpdesk.controller.subscription;

import com.konohahelpdesk.dto.subscription.FeatureResponse;
import com.konohahelpdesk.service.subscription.FeatureService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Subscription Management → Features (read-only catalog, defined in app-config.properties). */
@RestController
@RequestMapping("/api/admin/features")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('PRIV_MANAGE_PLANS')")
public class FeatureController {

    private final FeatureService featureService;

    public FeatureController(FeatureService featureService) {
        this.featureService = featureService;
    }

    @GetMapping
    public ResponseEntity<List<FeatureResponse>> list() {
        return ResponseEntity.ok(featureService.listAll());
    }
}
