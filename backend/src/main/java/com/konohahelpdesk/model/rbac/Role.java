package com.konohahelpdesk.model.rbac;

import jakarta.persistence.*;

/**
 * A grantable role row (RoleID, Role name) — one per {@link com.konohahelpdesk.model.user.Roles}
 * constant, seeded on startup by {@code RoleSeeder}. Exists as its own table (rather than just
 * using the Roles enum) so a user can be linked to it via {@link UserRole}: an additive grant of
 * extra permissions on top of their primary {@code User.role}.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Matches a {@link com.konohahelpdesk.model.user.Roles} enum constant name, e.g. "ADMIN". */
    @Column(nullable = false, unique = true, length = 30)
    private String name;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
