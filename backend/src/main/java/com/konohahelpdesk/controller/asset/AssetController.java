package com.konohahelpdesk.controller.asset;

import com.konohahelpdesk.dto.asset.AssetCompanyRow;
import com.konohahelpdesk.dto.asset.AssetRequest;
import com.konohahelpdesk.dto.asset.AssetResponse;
import com.konohahelpdesk.model.asset.AssetAssignment;
import com.konohahelpdesk.model.asset.AssetStatus;
import com.konohahelpdesk.model.asset.AssetType;
import com.konohahelpdesk.model.asset.WarrantyState;
import com.konohahelpdesk.security.CurrentUser;
import com.konohahelpdesk.service.asset.AssetService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * The customer asset register.
 *
 * !! READ THIS BEFORE ADDING A METHOD !!
 *
 * Unlike ContractController, this class carries NO class-level {@code @PreAuthorize}, because
 * customer users legitimately read from it — a blanket staff gate here would lock them out of
 * their own equipment list. {@code SecurityConfig} still denies /api/** to anyone without a role.
 *
 * The consequence is that the usual safety net is gone: EVERY WRITE METHOD MUST CARRY ITS OWN
 * {@code @PreAuthorize}. A new endpoint added without one is reachable by any signed-in customer.
 * Reads are scoped inside AssetService (company scope + the ASSET_MANAGEMENT plan feature), which
 * is the real enforcement either way.
 */
@RestController
@RequestMapping("/api/assets")
public class AssetController {

    /** The gate every write method uses. Kept as one constant so they can't drift apart. */
    private static final String CAN_WRITE =
        "hasAnyRole('ADMIN','ENGINEER') or hasAuthority('PRIV_MANAGE_ASSETS')";

    private final AssetService assets;
    private final CurrentUser currentUser;

    public AssetController(AssetService assets, CurrentUser currentUser) {
        this.assets = assets;
        this.currentUser = currentUser;
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    /**
     * The company picker: every company the caller may work in, with its asset and warranty
     * counts. Scoped in the service — an engineer sees the companies they're assigned to.
     */
    @GetMapping("/companies")
    public ResponseEntity<List<AssetCompanyRow>> companies() {
        return ResponseEntity.ok(assets.companiesWithCounts(currentUser.get()));
    }

    /**
     * One company's assets. {@code companyId} is omitted only by the cross-company views; the
     * normal path always passes it, because the UI is company-first.
     */
    @GetMapping
    public ResponseEntity<List<AssetResponse>> list(
        @RequestParam(required = false) Long companyId,
        @RequestParam(required = false) List<AssetType> type,
        @RequestParam(required = false) List<AssetStatus> status,
        @RequestParam(required = false) AssetAssignment assignedTo,
        @RequestParam(required = false) Long ownerId,
        @RequestParam(name = "groupId", required = false) List<Long> groupIds,
        @RequestParam(required = false, defaultValue = "false") boolean ungrouped,
        @RequestParam(required = false) WarrantyState warrantyState,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate warrantyEndFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate warrantyEndTo,
        @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(assets.list(currentUser.get(), companyId, type, status,
            assignedTo, ownerId, groupIds, ungrouped, warrantyState, warrantyEndFrom, warrantyEndTo, q));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssetResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(assets.get(currentUser.get(), id));
    }

    // ── Write — each one gates itself, see the class note above ──────────────

    @PostMapping
    @PreAuthorize(CAN_WRITE)
    public ResponseEntity<AssetResponse> create(@Valid @RequestBody AssetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assets.create(request, currentUser.get()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    public ResponseEntity<AssetResponse> update(@PathVariable Long id, @Valid @RequestBody AssetRequest request) {
        return ResponseEntity.ok(assets.update(id, request, currentUser.get()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        assets.delete(id, currentUser.get());
        return ResponseEntity.noContent().build();
    }
}
