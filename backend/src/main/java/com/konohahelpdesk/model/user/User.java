package com.konohahelpdesk.model.user;

import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.media.MediaFile;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
@Table(name = "users", indexes = {
        // The two shapes every list query uses: "this company's people" (team page, seat counts,
        // the companies list) and "all admins/engineers" (assignee pickers, breach escalation).
        @Index(name = "ix_users_company", columnList = "company_id"),
        @Index(name = "ix_users_role_status", columnList = "role, status")
})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Not updatable=false: a one-time startup migration renumbers legacy codes to the
    // EMP/CUS scheme (see UserCodeMigrationSeeder) — normal app code never changes it after creation.
    @Column(nullable = false, unique = true)
    private String userCode;

    // Not unique = true here: DB-level email uniqueness is enforced by a partial index
    // (see EmailUniqueIndexMigration) that only covers non-IS_DELETED rows, so a removed
    // team member's email can be reused for a new invite later. The app-level checks
    // (existsByEmailIgnoreCaseAndStatusNot, etc.) already only ever look at live users.
    @Column(nullable = false)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Roles role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.INVITED;

    @ManyToOne
    @JoinColumn(name = "company_id", nullable = true)
    private Company company;

    /**
     * True while the account still has a password it didn't choose itself — the built-in default
     * admin (admin@admin.com / admin, see AdminBootstrap). Such a user can only reach /auth/me,
     * /auth/change-password and /auth/logout until they set their own password (JwtCookieFilter).
     */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    /**
     * When the password was last changed or reset. Auth cookies issued before this instant are
     * refused (JwtCookieFilter), so a password change signs out every other session — including
     * one opened with the default admin/admin before it was replaced.
     */
    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    /** MediaFile id of the profile photo (null = none). */
    @Column(name = "photo_id", length = 36)
    private String photoId;

    // ── Notification preferences ─────────────────────────────────────────
    @Column(name = "notify_email", nullable = false, columnDefinition = "boolean not null default true") private boolean notifyEmail = true;
    @Column(name = "notify_browser", nullable = false, columnDefinition = "boolean not null default false") private boolean notifyBrowser = false;
    @Column(name = "notify_on_assign", nullable = false, columnDefinition = "boolean not null default true") private boolean notifyOnAssign = true;
    @Column(name = "notify_on_reply", nullable = false, columnDefinition = "boolean not null default true") private boolean notifyOnReply = true;
    @Column(name = "notify_on_resolve", nullable = false, columnDefinition = "boolean not null default true") private boolean notifyOnResolve = true;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public String getUserCode() { 
        return userCode; 
    }

    public void setUserCode(String userCode) { 
        this.userCode = userCode; 
    }

    public String getFirstName() { 
        return firstName; 
    }

    public void setFirstName(String firstName) { 
        this.firstName = firstName; 
    }

    public String getLastName() {
        return lastName; 
    }

    public void setLastName(String lastName) { 
        this.lastName = lastName; 
    }

    public String getEmail() { 
        return email; 
    }

    public void setEmail(String email) { 
        this.email = email; 
    }

    public String getPhone() { 
        return phone; 
    }

    public void setPhone(String phone) { 
        this.phone = phone; 
    }

    @JsonIgnore
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) { 
        this.password = password; 
    }

    public Roles getRole() { 
        return role; 
    }

    public void setRole(Roles role) { 
        this.role = role; 
    }

    public UserStatus getStatus() { 
        return status; 
    }

    public void setStatus(UserStatus status) { 
        this.status = status; 
    }

    public Company getCompany() { 
        return company; 
    }

    public void setCompany(Company company) { 
        this.company = company; 
    }

    public String getPhotoId() {
        return photoId;
    }

    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public LocalDateTime getPasswordChangedAt() { return passwordChangedAt; }
    public void setPasswordChangedAt(LocalDateTime passwordChangedAt) { this.passwordChangedAt = passwordChangedAt; }

    public boolean isNotifyEmail() { return notifyEmail; }
    public void setNotifyEmail(boolean v) { this.notifyEmail = v; }
    public boolean isNotifyBrowser() { return notifyBrowser; }
    public void setNotifyBrowser(boolean v) { this.notifyBrowser = v; }
    public boolean isNotifyOnAssign() { return notifyOnAssign; }
    public void setNotifyOnAssign(boolean v) { this.notifyOnAssign = v; }
    public boolean isNotifyOnReply() { return notifyOnReply; }
    public void setNotifyOnReply(boolean v) { this.notifyOnReply = v; }
    public boolean isNotifyOnResolve() { return notifyOnResolve; }
    public void setNotifyOnResolve(boolean v) { this.notifyOnResolve = v; }

    public void setPhotoId(String photoId) {
        this.photoId = photoId;
    }

    public LocalDateTime getCreatedAt() { 
        return createdAt; 
    }

    public LocalDateTime getUpdatedAt() { 
        return updatedAt; 
    }

}