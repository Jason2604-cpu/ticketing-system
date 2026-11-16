package com.konohahelpdesk.controller.asset;

import com.konohahelpdesk.dto.asset.AssetGroupRequest;
import com.konohahelpdesk.dto.asset.AssetGroupResponse;
import com.konohahelpdesk.security.CurrentUser;
import com.konohahelpdesk.service.asset.AssetGroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Asset groups. Same rule as {@link AssetController}: no class-level gate because reads are
 * scoped in the service, so EVERY WRITE METHOD MUST CARRY ITS OWN {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/api/asset-groups")
public class AssetGroupController {

    private static final String CAN_WRITE =
        "hasAnyRole('ADMIN','ENGINEER') or hasAuthority('PRIV_MANAGE_ASSETS')";

    private final AssetGroupService groups;
    private final CurrentUser currentUser;

    public AssetGroupController(AssetGroupService groups, CurrentUser currentUser) {
        this.groups = groups;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ResponseEntity<List<AssetGroupResponse>> list(@RequestParam Long companyId) {
        return ResponseEntity.ok(groups.list(currentUser.get(), companyId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssetGroupResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(groups.get(currentUser.get(), id));
    }

    @PostMapping
    @PreAuthorize(CAN_WRITE)
    public ResponseEntity<AssetGroupResponse> create(@Valid @RequestBody AssetGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groups.create(request, currentUser.get()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    public ResponseEntity<AssetGroupResponse> update(@PathVariable Long id, @Valid @RequestBody AssetGroupRequest request) {
        return ResponseEntity.ok(groups.update(id, request, currentUser.get()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        groups.delete(id, currentUser.get());
        return ResponseEntity.noContent().build();
    }
}
