package com.konohahelpdesk.controller.rbac;

import com.konohahelpdesk.dto.rbac.RoleRequest;
import com.konohahelpdesk.dto.rbac.RoleResponse;
import com.konohahelpdesk.service.rbac.RoleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Role Management: admin-created custom roles with a hand-picked set of privileges, grantable to
 * staff on top of their primary role. ADMIN, or anyone holding the MANAGE_ROLES privilege itself.
 */
@RestController
@RequestMapping("/api/roles")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('PRIV_MANAGE_ROLES')")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /** Every role — the four system roles plus every custom one. */
    @GetMapping
    public ResponseEntity<List<RoleResponse>> list() {
        return ResponseEntity.ok(roleService.listAllRoles());
    }

    @PostMapping
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.createRole(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoleResponse> update(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(roleService.updateRole(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }
}
