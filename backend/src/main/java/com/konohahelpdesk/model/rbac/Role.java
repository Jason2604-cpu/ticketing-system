package com.konohahelpdesk.model.rbac;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

/**
 * A grantable role row. Two kinds share this table:
 *  - System rows (one per {@link com.konohahelpdesk.model.user.Roles} constant, seeded on startup
 *    by {@code RoleSeeder}, {@code custom = false}) — exist so a user can be linked to one via
 *    {@link UserRole} as an additive grant on top of their primary {@code User.role} (e.g. an
 *    ENGINEER granted ADMIN). Their access still comes from the matching {@code ROLE_*} authority.
 *  - Custom rows ({@code custom = true}), created by an admin through Role Management with a
 *    hand-picked set of {@link Privilege}s ticked. Granting one to a user adds a {@code PRIV_*}
 *    authority per privilege (see {@code JwtCookieFilter}) — real enforcement, not a label.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** System row: matches a {@link com.konohahelpdesk.model.user.Roles} constant, e.g. "ADMIN".
     *  Custom row: the admin-chosen display name, e.g. "Support Lead". */
    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @Column(length = 200)
    private String description;

    /** False for the four seeded system rows; true for anything created via Role Management. */
    @Column(nullable = false)
    private boolean custom = false;

    /** Only meaningful for custom rows — system rows carry no explicit privileges. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_privileges", joinColumns = @JoinColumn(name = "role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "privilege", length = 30)
    private Set<Privilege> privileges = new HashSet<>();

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isCustom() { return custom; }
    public void setCustom(boolean custom) { this.custom = custom; }
    public Set<Privilege> getPrivileges() { return privileges; }
    public void setPrivileges(Set<Privilege> privileges) { this.privileges = privileges == null ? new HashSet<>() : privileges; }
}
