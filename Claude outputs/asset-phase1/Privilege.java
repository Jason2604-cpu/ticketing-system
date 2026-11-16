package com.konohahelpdesk.model.rbac;

/**
 * A single grantable capability a custom {@link Role} can carry. Unlike the four primary
 * {@link com.konohahelpdesk.model.user.Roles} (which stay hard-wired into {@code @PreAuthorize}
 * checks), privileges are ticked on an admin-created role and checked via a {@code PRIV_*}
 * granted authority (see {@code JwtCookieFilter}) — real, additive enforcement, never cosmetic.
 */
public enum Privilege {
    /** Everything under /api/users — invite, edit, deactivate, remove, assign companies/roles. */
    MANAGE_USERS("Manage Users", "Create, edit, deactivate and remove users; assign companies and roles"),
    /** Everything under /api/companies — create/edit companies, logos, subscriptions. */
    MANAGE_COMPANIES("Manage Companies", "Create and edit companies, their logo and subscription"),
    /** Plans, features and per-company subscription overrides. */
    MANAGE_PLANS("Manage Plans & Billing", "Create and edit subscription plans, features and billing overrides"),
    /** Creating/editing/deleting custom roles and granting them to users. */
    MANAGE_ROLES("Manage Roles", "Create custom roles, tick their privileges, and grant them to staff"),
    /** Everything under /api/contracts — recurring service contracts and their visit schedules. */
    MANAGE_CONTRACTS("Manage Contracts", "Create and edit recurring service contracts, their schedules and renewals"),
    /** Read-only access to /api/audit — the security and activity trail. Never grants write anywhere. */
    VIEW_AUDIT_LOG("View Audit Log", "Read the security and activity trail: sign-ins, permission changes, record edits"),
    /** Everything under /api/templates — ticket templates and canned replies. */
    MANAGE_TEMPLATES("Manage Templates", "Create and edit ticket templates and canned replies for the team"),
    /**
     * Writes under /api/assets — the customer asset register. Reading assets is NOT gated on this:
     * customers see their own equipment through their subscription's ASSET_MANAGEMENT feature, and
     * staff read within their company scope. This privilege is what lets a non-admin create, edit
     * and delete them.
     */
    MANAGE_ASSETS("Manage Assets", "Register and edit customer assets, their warranties and documents");

    private final String label;
    private final String description;

    Privilege(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }
}
