package com.konohahelpdesk.service.rbac;

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
 * roles — e.g. an ENGINEER can also be granted ADMIN privileges. Granted roles are unioned with
 * the primary role when authorities are built (see {@code JwtCookieFilter}).
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

    /** The extra roles granted to one user, beyond their primary role. */
    public List<Roles> additionalRoles(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
            .map(ur -> Roles.valueOf(ur.getRole().getName()))
            .sorted()
            .toList();
    }

    /** Batch form of {@link #additionalRoles(Long)} — one query for a whole user listing. */
    public Map<Long, List<Roles>> additionalRolesByUser(Collection<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        Map<Long, List<Roles>> byUser = new HashMap<>();
        for (UserRole ur : userRoleRepository.findByUserIdIn(userIds)) {
            byUser.computeIfAbsent(ur.getUser().getId(), k -> new ArrayList<>()).add(Roles.valueOf(ur.getRole().getName()));
        }
        byUser.replaceAll((id, roles) -> roles.stream().sorted().toList());
        return byUser;
    }

    /** All roles that actually apply to this user — their primary role plus every additive grant. */
    public Set<Roles> effectiveRoles(User user) {
        EnumSet<Roles> roles = EnumSet.of(user.getRole());
        roles.addAll(additionalRoles(user.getId()));
        return roles;
    }

    /** Replaces the full set of additive roles for a user. The primary role is never stored here — passing it is a no-op for that entry. */
    @Transactional
    public void setAdditionalRoles(Long userId, Set<Roles> roles) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        Set<Roles> desired = roles == null ? Set.of() : roles.stream().filter(r -> r != user.getRole()).collect(Collectors.toSet());

        userRoleRepository.deleteByUserId(userId);
        for (Roles r : desired) {
            Role role = roleRepository.findByName(r.name())
                .orElseThrow(() -> new ResourceNotFoundException("Role not seeded: " + r.name()));
            UserRole ur = new UserRole();
            ur.setUser(user);
            ur.setRole(role);
            userRoleRepository.save(ur);
        }
        audit.event("USER_ROLES_CHANGED", "userId=" + userId + " additionalRoles=" + desired);
    }
}
