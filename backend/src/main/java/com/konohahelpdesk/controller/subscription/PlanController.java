package com.konohahelpdesk.controller.subscription;

import com.konohahelpdesk.dto.subscription.PlanRequest;
import com.konohahelpdesk.dto.subscription.PlanResponse;
import com.konohahelpdesk.model.plan.PlanStatus;
import com.konohahelpdesk.service.subscription.PlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Subscription Management → Plans. ADMIN only. */
@RestController
@RequestMapping("/api/admin/plans")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('PRIV_MANAGE_PLANS')")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public ResponseEntity<List<PlanResponse>> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(planService.list(includeInactive));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(planService.get(id));
    }

    @PostMapping
    public ResponseEntity<PlanResponse> create(@Valid @RequestBody PlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlanResponse> update(@PathVariable Long id, @Valid @RequestBody PlanRequest request) {
        return ResponseEntity.ok(planService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PlanResponse> setStatus(@PathVariable Long id, @RequestParam PlanStatus status) {
        return ResponseEntity.ok(planService.setStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        planService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
