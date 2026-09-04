package com.konohahelpdesk.controller.subscription;

import com.konohahelpdesk.model.plan.Plan;

import com.konohahelpdesk.dto.subscription.LimitsDto;
import com.konohahelpdesk.dto.subscription.SubscriptionDefaultsResponse;
import com.konohahelpdesk.service.subscription.SubscriptionValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Values the admin UI needs to render the Custom Plan form (pre-fills + caps). */
@RestController
@RequestMapping("/api/admin/subscriptions")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('PRIV_MANAGE_PLANS')")
public class SubscriptionAdminController {

    private final SubscriptionValidator validator;

    public SubscriptionAdminController(SubscriptionValidator validator) {
        this.validator = validator;
    }

    @GetMapping("/defaults")
    public ResponseEntity<SubscriptionDefaultsResponse> defaults() {
        SubscriptionDefaultsResponse r = new SubscriptionDefaultsResponse();
        r.setCustomDefaults(validator.customDefaultLimits());
        r.setCustomDefaultSla(validator.customDefaultSla());
        r.setCaps(new LimitsDto(validator.caps().getMaxUsers(), validator.caps().getMonthlyTicketLimit(), validator.caps().getStorageLimitMb()));
        r.setSlaResponseMinutesCap(validator.caps().getSlaResponseMinutes());
        r.setSlaResolutionMinutesCap(validator.caps().getSlaResolutionMinutes());
        return ResponseEntity.ok(r);
    }
}
