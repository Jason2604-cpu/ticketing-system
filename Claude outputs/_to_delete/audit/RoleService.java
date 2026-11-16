package com.konohahelpdesk.service.rbac;

import com.konohahelpdesk.dto.rbac.RoleRequest;
import com.konohahelpdesk.dto.rbac.RoleResponse;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.exception.DuplicateResourceException;
import com.konohahelpdesk.exception.ResourceNotFoundException;
import com.konohahelpdesk.model.rbac.Role;
import com.konohahelpdesk.model.rbac.UserRole;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.repository.rbac.RoleRepository;
import com.konohahelpdesk.repository.rbac.UserRoleRepository;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Additive RBAC: on top of a user's single primary {@code User.role} (which still drives
 * {@code @PreAuthorize} checks and the default portal a user lands in), an ADMIN can grant extra
 * roles — e.g. an ENGINEER can also be granted ADMIN privileges — and, separately, admin-created
 * custom roles carrying hand-picked {@link com.konohahelpdesk.model.rbac.Privilege}s (Role
 * Management). Both kinds of grant live in the same {@code UserRole} table, kept independent via
 * the {@code role.custom} flag so replacing one set never touches the other.
 */
@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final AuditLogger audit;

    public RoleService(RoleRepository roleRepository, UserRoleRepository userRoleRepository,
                       UserRepository userRepository, AuditLogger audit) {
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.userRepository = userRepository;
        this.audit = audit;
    }

    // ── Fixed-enum additive roles (Admin <-> Engineer) ──────────────────────

    /** The extra system roles granted to one user, beyond their primary role. */
    public List<Roles> additionalRoles(Long userId) {
        return userRoleRepository.findByUserIdAndRole_Custom(userId, false).stream()
            .map(ur -> Roles.valueOf(ur.getRole().getName()))
            .sorted()
            .toList();
    }

    /** Batch form of {@link #additionalRoles(Long)} — one query for a whole user listing. */
    public Map<Long, List<Roles>> additionalRolesByUser(Collection<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        Map<Long, List<Roles>> byUser = new HashMap<>();
        for (UserRole ur : userRoleRepository.findByUserIdInAndRole_Custom(userIds, false)) {
            byUser.computeIfAbsent(ur.getUser().getId(), k -> new ArrayList<>()).add(Roles.valueOf(ur.getRole().getName()));
        }
        byUser.replaceAll((id, roles) -> roles.stream().sorted().toList());
        return byUser;
    }


    /**
     * Which additive roles make sense on top of a given primary role. The two "sides" of the
     * merged user-code scheme behave differently:
     *  - Staff (ADMIN/ENGINEER, the EMPxxxxx side) can hold BOTH at once — that's the whole point
     *    of merging them, e.g. an ENGINEER can also be granted ADMIN privileges.
     *  - Customer-side (CUSTOMER/CUSTOMER_ADMIN, the CUSxxxxxx side) are mutually exclusive tiers
     *    of the same relationship — a user is never both at once, so no additive role applies here.
     *    Moving someone between them means changing their primary role, not granting an extra one.
     */
    private Set<Roles> allowedAdditionalRoles(Roles primary) {
        return switch (primary) {
            case ADMIN -> EnumSet.of(Roles.ENGINEER);
            case ENGINEER -> EnumSet.of(Roles.ADMIN);
            case CUSTOMER, CUSTOMER_ADMIN -> EnumSet.noneOf(Roles.class);
        };
    }

    /** Replaces the full set of additive system roles for a user. The primary role is never stored here. */
    @Transactional
    public void setAdditionalRoles(Long userId, Set<Roles> roles) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        assertNotSelf(userId);
        Set<Roles> desired = roles == null ? Set.of() : roles.stream().filter(r -> r != user.getRole()).collect(Collectors.toSet());

        Set<Roles> allowed = allowedAdditionalRoles(user.getRole());
        Set<Roles> invalid = desired.stream().filter(r -> !allowed.contains(r)).collect(Collectors.toSet());
        if (!invalid.isEmpty()) {
            throw new BusinessRuleException(user.getRole() == Roles.CUSTOMER || user.getRole() == Roles.CUSTOMER_ADMIN
                ? "CUSTOMER and CUSTOMER_ADMIN are mutually exclusive — change the user's primary role instead of granting an additional one"
                : "Staff can only additionally hold the other staff role (ADMIN <-> ENGINEER): invalid role(s) " + invalid);
        }

        userRoleRepository.deleteByUserIdAndRole_Custom(userId, false);
        for (Roles r : desired) {
            Role role = roleRepository.findByName(r.name())
                .orElseThrow(() -> new ResourceNotFoundException("Role not seeded: " + r.name()));
            grant(user, role);
        }
        audit.event("USER_ROLES_CHANGED", "userId=" + userId + " additionalRoles=" + desired);
    }

    // ── Custom roles (Role Management) ──────────────────────────────────────

    public List<RoleResponse> listAllRoles() {
        return roleRepository.findAllByOrderByNameAsc().stream().map(RoleResponse::from).toList();
    }

    /** The custom roles granted to one user. */
    public List<RoleResponse.Ref> customRoles(Long userId) {
        return userRoleRepository.findByUserIdAndRole_Custom(userId, true).stream()
            .map(ur -> RoleResponse.Ref.from(ur.getRole()))
            .sorted(Comparator.comparing(RoleResponse.Ref::name))
            .toList();
    }

    /** Batch form of {@link #customRoles(Long)} — one query for a whole user listing. */
    public Map<Long, List<RoleResponse.Ref>> customRolesByUser(Collection<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        Map<Long, List<RoleResponse.Ref>> byUser = new HashMap<>();
        for (UserRole ur : userRoleRepository.findByUserIdInAndRole_Custom(userIds, true)) {
            byUser.computeIfAbsent(ur.getUser().getId(), k -> new ArrayList<>()).add(RoleResponse.Ref.from(ur.getRole()));
        }
        byUser.replaceAll((id, refs) -> refs.stream().sorted(Comparator.comparing(RoleResponse.Ref::name)).toList());
        return byUser;
    }

    @Transactional
    public RoleResponse createRole(RoleRequest req) {
        String name = req.getName() == null ? "" : req.getName().trim();
        if (name.isEmpty()) throw new BusinessRuleException("Role name is required");
        if (roleRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("A role named \"" + name + "\" already exists");
        }
        Role role = new Role();
        role.setName(name);
        role.setDescription(blankToNull(req.getDescription()));
        role.setCustom(true);
        role.setPrivileges(req.getPrivileges() == null ? Set.of() : Set.copyOf(req.getPrivileges()));
        Role saved = roleRepository.save(role);
        audit.event("ROLE_CREATED", "roleId=" + saved.getId() + " name=" + name + " privileges=" + saved.getPrivileges());
        return RoleResponse.from(saved);
    }

    @Transactional
    public RoleResponse updateRole(Long id, RoleRequest req) {
        Role role = roleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));
        if (!role.isCustom()) {
            throw new BusinessRuleException("System roles (Admin, Engineer, Company Admin, Customer) can't be edited");
        }
        String name = req.getName() == null ? "" : req.getName().trim();
        if (name.isEmpty()) throw new BusinessRuleException("Role name is required");
        if (!name.equalsIgnoreCase(role.getName()) && roleRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("A role named \"" + name + "\" already exists");
        }
        role.setName(name);
        role.setDescription(blankToNull(req.getDescription()));
        role.setPrivileges(req.getPrivileges() == null ? Set.of() : Set.copyOf(req.getPrivileges()));
        Role saved = roleRepository.save(role);
        audit.event("ROLE_UPDATED", "roleId=" + id + " name=" + name + " privileges=" + saved.getPrivileges());
        return RoleResponse.from(saved);
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = roleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));
        if (!role.isCustom()) {
            throw new BusinessRuleException("System roles (Admin, Engineer, Company Admin, Customer) can't be deleted");
        }
        userRoleRepository.deleteByRoleId(id);
        roleRepository.delete(role);
        audit.event("ROLE_DELETED", "roleId=" + id + " name=" + role.getName());
    }

    /** Replaces the full set of custom roles granted to a user. Only staff (ADMIN/ENGINEER) can hold one. */
    @Transactional
    public void setCustomRoles(Long userId, Set<Long> roleIds) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        assertNotSelf(userId);
        Set<Long> desired = roleIds == null ? Set.of() : roleIds;
        if (!desired.isEmpty() && user.getRole() != Roles.ADMIN && user.getRole() != Roles.ENGINEER) {
            throw new BusinessRuleException("Custom roles can only be granted to staff (Admin/Engineer)");
        }

        userRoleRepository.deleteByUserIdAndRole_Custom(userId, true);
        for (Long roleId : desired) {
            Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));
            if (!role.isCustom()) {
                throw new BusinessRuleException("\"" + role.getName() + "\" is a system role — grant it via Additional roles instead");
            }
            grant(user, role);
        }
        audit.event("USER_CUSTOM_ROLES_CHANGED", "userId=" + userId + " roleIds=" + desired);
    }

    /**
     * Nobody edits their own grants — not even an admin. Defence in depth behind the ADMIN-only
     * @PreAuthorize on the two endpoints: if that annotation is ever loosened back to MANAGE_USERS,
     * this still stops the self-escalation, and it keeps the audit trail honest (every grant was
     * made by someone other than its beneficiary).
     */
    private void assertNotSelf(Long userId) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User me && me.getId().equals(userId)) {
            throw new BusinessRuleException("You can't change your own roles — ask another admin.");
        }
    }

    private void grant(User user, Role role) {
        UserRole ur = new UserRole();
        ur.setUser(user);
        ur.setRole(role);
        userRoleRepository.save(ur);
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
