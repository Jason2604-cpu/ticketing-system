package com.konohahelpdesk.security;

import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Convenience access to the authenticated user + common authorization guards. */
@Component
public class CurrentUser {

    public User get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new AuthenticationCredentialsNotFoundException("Not authenticated");
        }
        return user;
    }

    public boolean isAdmin() {
        return get().getRole() == Roles.ADMIN;
    }

    /**
     * Mirrors the {@code @PreAuthorize} on ContractController, for the one place the check has to
     * happen in code rather than on a method: serving a contract document out of /api/media/{id}.
     * Reads the granted authorities rather than the user row, so a MANAGE_CONTRACTS privilege
     * arriving through a custom role counts exactly as it does everywhere else.
     */
    public boolean canManageContracts() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "PRIV_MANAGE_CONTRACTS".equals(a.getAuthority()));
    }

    /**
     * ADMIN and ENGINEER may act on any company. CUSTOMER_ADMIN / CUSTOMER only on their own.
     * Prevents IDOR: a customer guessing another company's id gets 403, not data.
     */
    public void requireCompanyAccess(Long companyId) {
        User user = get();
        if (user.getRole() == Roles.ADMIN || user.getRole() == Roles.ENGINEER) {
            return;
        }
        if (user.getCompany() == null || !user.getCompany().getId().equals(companyId)) {
            throw new AccessDeniedException("Forbidden");
        }
    }
}
