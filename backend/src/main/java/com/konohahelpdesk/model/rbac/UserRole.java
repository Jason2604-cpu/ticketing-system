package com.konohahelpdesk.model.rbac;

import com.konohahelpdesk.model.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A single additive role grant: {@code user} also has the permissions of {@code role}, on top of
 * their primary {@code User.role}. E.g. an ENGINEER granted the ADMIN row here gets admin
 * privileges too, without their primary role (and everything keyed off it — user code prefix,
 * default screens) changing.
 */
@Entity
@Table(name = "user_roles", uniqueConstraints = @UniqueConstraint(name = "uk_user_role", columnNames = {"user_id", "role_id"}))
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
