package com.konohahelpdesk.controller.contract;

import com.konohahelpdesk.dto.contract.ContractRequest;
import com.konohahelpdesk.dto.contract.ContractResponse;
import com.konohahelpdesk.dto.contract.VisitRequest;
import com.konohahelpdesk.model.contract.ContractState;
import com.konohahelpdesk.security.CurrentUser;
import com.konohahelpdesk.service.contract.ContractService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Recurring service contracts — preventive maintenance schedules, retainers, annual
 * certifications — and their generated visit schedules.
 *
 * ADMIN, or anyone holding the MANAGE_CONTRACTS privilege through a custom role. Applied at the
 * class level so a method added later can't accidentally ship without a check; the same
 * {@code hasRole or hasAuthority} shape the rest of the admin surface uses.
 */
@RestController
@RequestMapping("/api/contracts")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('PRIV_MANAGE_CONTRACTS')")
public class ContractController {

    private final ContractService contracts;
    private final CurrentUser currentUser;

    public ContractController(ContractService contracts, CurrentUser currentUser) {
        this.contracts = contracts;
        this.currentUser = currentUser;
    }

    /** All contracts, optionally filtered by company, derived state, or a free-text search. */
    @GetMapping
    public ResponseEntity<List<ContractResponse>> list(
        @RequestParam(required = false) Long companyId,
        @RequestParam(required = false) ContractState state,
        @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(contracts.list(companyId, state, q));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContractResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(contracts.get(id));
    }

    @PostMapping
    public ResponseEntity<ContractResponse> create(@Valid @RequestBody ContractRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contracts.create(request, currentUser.get()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContractResponse> update(@PathVariable Long id, @Valid @RequestBody ContractRequest request) {
        return ResponseEntity.ok(contracts.update(id, request, currentUser.get()));
    }

    /** Rebuilds the schedule from the contract's dates, keeping visits already completed or ticketed. */
    @PostMapping("/{id}/schedule")
    public ResponseEntity<ContractResponse> regenerate(@PathVariable Long id) {
        return ResponseEntity.ok(contracts.regenerateSchedule(id, currentUser.get()));
    }

    /** Mark one occurrence done/skipped, move its date, or annotate it. */
    @PutMapping("/{id}/visits/{visitId}")
    public ResponseEntity<ContractResponse> updateVisit(@PathVariable Long id, @PathVariable Long visitId,
                                                        @Valid @RequestBody VisitRequest request) {
        return ResponseEntity.ok(contracts.updateVisit(id, visitId, request, currentUser.get()));
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContractResponse> attach(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(contracts.attach(id, file, currentUser.get()));
    }

    @DeleteMapping("/{id}/documents/{mediaId}")
    public ResponseEntity<ContractResponse> removeAttachment(@PathVariable Long id, @PathVariable String mediaId) {
        return ResponseEntity.ok(contracts.removeAttachment(id, mediaId, currentUser.get()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        contracts.delete(id, currentUser.get());
        return ResponseEntity.noContent().build();
    }
}
