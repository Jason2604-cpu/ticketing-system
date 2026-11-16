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
     * The asset-write counterpart of {@link #canManageContracts()}, for the places the check has
     * to happen in code rather than on a method — serving an asset document out of /api/media/{id}.
     *
     * ENGINEER counts here but not in canManageContracts: contracts are the provider's commercial
     * record, while maintaining a customer's equipment list is ordinary engineer work.
     */
    public boolean canManageAssets() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())
                        || "ROLE_ENGINEER".equals(a.getAuthority())
                        || "PRIV_MANAGE_ASSETS".equals(a.getAuthority()));
    }

    /** True when the signed-in user's granted authorities include this one ({@code ROLE_*} or {@code PRIV_*}). */
    public boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    /** Custom-role privileges whose pages work across every customer company (and need its pickers). */
    private static final java.util.Set<String> COMPANY_WIDE_PRIVILEGES = java.util.Set.of(
        "PRIV_MANAGE_COMPANIES", "PRIV_MANAGE_USERS", "PRIV_MANAGE_CONTRACTS", "PRIV_MANAGE_ASSETS", "PRIV_MANAGE_TEMPLATES",
        "PRIV_MANAGE_KNOWLEDGE_BASE");

    /**
     * True when the signed-in user holds, through a custom role, a privilege that works across all
     * customer companies. This is what lets a Default employee (role STAFF) who was given, say,
     * "Manage Contracts" see the companies those pages pick from — and nothing more.
     */
    public boolean hasCompanyWidePrivilege() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> COMPANY_WIDE_PRIVILEGES.contains(a.getAuthority()));
    }

    /**
     * ADMIN and ENGINEER may act on any company; a Default employee only with a company-wide
     * privilege (see above). CUSTOMER_ADMIN / CUSTOMER only on their own.
     * Prevents IDOR: a customer guessing another company's id gets 403, not data.
     */
    public void requireCompanyAccess(Long companyId) {
        User user = get();
        if (user.getRole().worksTickets()) {
            return;
        }
        if (user.getRole() == Roles.STAFF) {
            if (hasCompanyWidePrivilege()) return;
            throw new AccessDeniedException("Forbidden");
        }
        if (user.getCompany() == null || !user.getCompany().getId().equals(companyId)) {
            throw new AccessDeniedException("Forbidden");
        }
    }
}
