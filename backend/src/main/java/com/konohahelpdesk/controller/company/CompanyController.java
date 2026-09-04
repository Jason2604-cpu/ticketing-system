package com.konohahelpdesk.controller.company;

import com.konohahelpdesk.model.company.Company;

import com.konohahelpdesk.dto.company.CompanyResponse;
import com.konohahelpdesk.dto.company.CreateCompanyRequest;
import com.konohahelpdesk.dto.company.UpdateCompanyRequest;
import com.konohahelpdesk.dto.subscription.CompanySubscriptionRequest;
import com.konohahelpdesk.dto.subscription.CompanySubscriptionResponse;
import com.konohahelpdesk.model.plan.SubscriptionStatus;
import com.konohahelpdesk.security.CurrentUser;
import com.konohahelpdesk.service.company.CompanyService;
import com.konohahelpdesk.service.subscription.CompanySubscriptionService;
import com.konohahelpdesk.service.media.ProfileMediaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Company Management + Company Subscriptions.
 *
 * Writes: ADMIN only.
 * Reads:  ADMIN/ENGINEER for any company; CUSTOMER_ADMIN/CUSTOMER only for their own
 *         (CurrentUser.requireCompanyAccess — stops IDOR by guessing ids).
 */
@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;
    private final CompanySubscriptionService subscriptionService;
    private final ProfileMediaService profileMedia;
    private final CurrentUser currentUser;

    public CompanyController(CompanyService companyService,
                             CompanySubscriptionService subscriptionService,
                             ProfileMediaService profileMedia,
                             CurrentUser currentUser) {
        this.companyService = companyService;
        this.subscriptionService = subscriptionService;
        this.profileMedia = profileMedia;
        this.currentUser = currentUser;
    }

    // ── Company ──────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PRIV_MANAGE_COMPANIES')")
    public ResponseEntity<CompanyResponse> create(@Valid @RequestBody CreateCompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(companyService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ENGINEER')")
    public ResponseEntity<List<CompanyResponse>> list() {
        return ResponseEntity.ok(companyService.list(currentUser.get()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CompanyResponse> get(@PathVariable Long id) {
        currentUser.requireCompanyAccess(id);
        return ResponseEntity.ok(companyService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PRIV_MANAGE_COMPANIES')")
    public ResponseEntity<CompanyResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateCompanyRequest request) {
        return ResponseEntity.ok(companyService.update(id, request));
    }

    // ── Logo ─────────────────────────────────────────────────────────────────

    /** multipart/form-data, part name "file". Replaces the current logo. */
    @PutMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PRIV_MANAGE_COMPANIES')")
    public ResponseEntity<CompanyResponse> uploadLogo(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(profileMedia.setCompanyLogo(id, file, currentUser.get().getId()));
    }

    @DeleteMapping("/{id}/logo")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PRIV_MANAGE_COMPANIES')")
    public ResponseEntity<CompanyResponse> deleteLogo(@PathVariable Long id) {
        return ResponseEntity.ok(profileMedia.removeCompanyLogo(id));
    }

    // ── Subscription ─────────────────────────────────────────────────────────

    @GetMapping("/{id}/subscription")
    public ResponseEntity<CompanySubscriptionResponse> getSubscription(@PathVariable Long id) {
        currentUser.requireCompanyAccess(id);
        return ResponseEntity.ok(subscriptionService.getResolved(id));
    }

    @PutMapping("/{id}/subscription")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PRIV_MANAGE_COMPANIES')")
    public ResponseEntity<CompanySubscriptionResponse> assignSubscription(@PathVariable Long id,
                                                                          @Valid @RequestBody CompanySubscriptionRequest request) {
        return ResponseEntity.ok(subscriptionService.assign(id, request));
    }

    @PatchMapping("/{id}/subscription/status")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PRIV_MANAGE_COMPANIES')")
    public ResponseEntity<CompanySubscriptionResponse> setSubscriptionStatus(@PathVariable Long id,
                                                                             @RequestParam SubscriptionStatus status) {
        return ResponseEntity.ok(subscriptionService.setStatus(id, status));
    }

    /** Lightweight check the frontend can use to show/hide feature UI. */
    @GetMapping("/{id}/features/{code}")
    public ResponseEntity<Map<String, Object>> hasFeature(@PathVariable Long id, @PathVariable String code) {
        currentUser.requireCompanyAccess(id);
        return ResponseEntity.ok(Map.of("featureCode", code.toUpperCase(), "enabled", subscriptionService.hasFeature(id, code)));
    }
}
