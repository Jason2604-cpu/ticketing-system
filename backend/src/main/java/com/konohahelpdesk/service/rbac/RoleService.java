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
 * Who may do what.
 *
 * Employees (the EMPxxxxx side) hold exactly ONE system role, stored on {@code User.role}:
 *  - ADMIN    — everything (it includes everything an Engineer can do),
 *  - ENGINEER — works tickets for their assigned companies,
 *  - STAFF    — "Default": signs in, no module at all.
 * On top of that an employee can be granted admin-created custom roles (Role Management), each
 * carrying hand-picked {@link com.konohahelpdesk.model.rbac.Privilege}s that open specific admin
 * pages — e.g. a Default employee with a "Contract Manager" role sees Service Contracts only.
 *
 * Customers (CUSTOMER / CUSTOMER_ADMIN) never get custom roles; the two are mutually exclusive
 * tiers switched with the Company Admin toggle (TeamService.setAdmin).
 *
 * (Before V3 an employee could also hold the other staff role as an "additional" grant. The
 * service layer only ever read the primary role, so an Engineer granted Admin had admin pages
 * but engineer-scoped data. V3 flattened those grants into the primary role.)
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

    // ── Employee roles: Admin / Engineer / Default + custom roles ──────────

    /**
     * Saves the Users page "Roles" checkboxes in one go.
     *
     * {@code systemRoles} may contain ADMIN and/or ENGINEER; the stored role is the highest one
     * ticked (Admin includes Engineer), and nothing ticked means Default (STAFF). Custom roles are
     * replaced with {@code customRoleIds} — except for an Admin, who already holds every privilege,
     * so any custom roles are dropped rather than kept as dead weight.
     *
     * @return the role the user had before, so the caller can tidy up engineer-only state
     */
    @Transactional
    public Roles setStaffRoles(Long userId, Set<Roles> systemRoles, Set<Long> customRoleIds) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        assertNotSelf(userId);
        if (!user.getRole().isStaffSide()) {
            throw new BusinessRuleException("Roles are for employees. A customer contact is either Company Admin or "
                + "Customer — use the Company Admin switch instead.");
        }
        Set<Roles> picked = systemRoles == null ? Set.of() : systemRoles;
        Set<Roles> invalid = picked.stream().filter(r -> !r.worksTickets()).collect(Collectors.toSet());
        if (!invalid.isEmpty()) {
            throw new BusinessRuleException("Only Admin and Engineer can be ticked (nothing ticked = Default): invalid role(s) " + invalid);
        }

        Roles previous = user.getRole();
        Roles next = picked.contains(Roles.ADMIN) ? Roles.ADMIN
                   : picked.contains(Roles.ENGINEER) ? Roles.ENGINEER
                   : Roles.STAFF;
        if (next != previous) {
            user.setRole(next);
            userRepository.save(user);
            audit.event("USER_ROLE_CHANGED", AuditLogger.describe(user) + " · " + previous.name() + " -> " + next.name());
        }
        replaceCustomRoles(user, next == Roles.ADMIN ? Set.of() : customRoleIds);
        return previous;
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
            throw new BusinessRuleException("System roles (Admin, Engineer, Default, Company Admin, Customer) can't be edited");
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
            throw new BusinessRuleException("System roles (Admin, Engineer, Default, Company Admin, Customer) can't be deleted");
        }
        userRoleRepository.deleteByRoleId(id);
        roleRepository.delete(role);
        audit.event("ROLE_DELETED", "roleId=" + id + " name=" + role.getName());
    }

    /**
     * Replaces the custom roles granted to an employee. Only the difference is written: the
     * (user_id, role_id) pair is unique, and Hibernate inserts IDENTITY rows immediately but
     * defers deletes, so "delete everything, re-insert the wanted set" would trip the constraint
     * for any role that stays.
     */
    private void replaceCustomRoles(User user, Set<Long> roleIds) {
        Set<Long> desired = roleIds == null ? Set.of() : Set.copyOf(roleIds);
        if (!desired.isEmpty() && !user.getRole().canHoldCustomRoles()) {
            throw new BusinessRuleException("Custom roles are for Engineer and Default employees — an Admin already has every privilege");
        }
        List<UserRole> current = userRoleRepository.findByUserIdAndRole_Custom(user.getId(), true);
        Set<Long> currentIds = current.stream().map(ur -> ur.getRole().getId()).collect(Collectors.toSet());
        if (currentIds.equals(desired)) return;

        // All the roles to add in one query, not one findById per ticked role.
        Set<Long> toAdd = desired.stream().filter(id -> !currentIds.contains(id)).collect(Collectors.toSet());
        List<Role> toGrant = toAdd.isEmpty() ? List.of() : roleRepository.findAllById(toAdd);
        if (toGrant.size() != toAdd.size()) {
            Set<Long> found = toGrant.stream().map(Role::getId).collect(Collectors.toSet());
            throw new ResourceNotFoundException("Role not found: " + toAdd.stream().filter(id -> !found.contains(id)).toList());
        }
        for (Role role : toGrant) {
            if (!role.isCustom()) {
                throw new BusinessRuleException("\"" + role.getName() + "\" is a system role — tick Admin or Engineer instead");
            }
        }

        List<String> keptNames = new ArrayList<>();
        for (UserRole ur : current) {
            if (desired.contains(ur.getRole().getId())) keptNames.add(ur.getRole().getName());
            else userRoleRepository.delete(ur);
        }
        userRoleRepository.flush();
        toGrant.forEach(role -> grant(user, role));

        // Names, not ids: the audit row has to stay readable after the role is renamed or deleted.
        List<String> names = new ArrayList<>(keptNames);
        toGrant.forEach(r -> names.add(r.getName()));
        names.sort(String::compareTo);
        audit.event("USER_CUSTOM_ROLES_CHANGED", AuditLogger.describe(user) + " · custom roles: "
            + (names.isEmpty() ? "none" : String.join(", ", names)));
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
