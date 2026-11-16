package com.konohahelpdesk.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.konohahelpdesk.dto.rbac.RoleResponse;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;

import java.time.LocalDateTime;

/** What the API exposes about a user — never the entity itself (no password hash, no lazy graphs). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    private Long id;
    private String userCode;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private Roles role;
    private UserStatus status;
    private Long companyId;
    private String companyName;
    /** GET this (with the auth cookie) to load the photo; null when none. */
    private String photoUrl;
    private NotificationPreferences notifications;
    /** True for the default admin until they replace admin/admin — the UI sends them to /change-password. */
    private boolean mustChangePassword;

    /**
     * Feature codes enabled by the user's company subscription (customers only; null for staff,
     * who are not gated by a plan). Sent with /auth/me so the client portal can hide what the
     * company hasn't paid for without a round trip per feature — the API still enforces it.
     */
    private java.util.List<String> companyFeatures;
    /** Engineers only: companies they look after. Populated by the admin listing. */
    private java.util.List<CompanyRef> assignedCompanies;
    /** Role Management: admin-created custom roles (with their privileges) granted to this user. */
    private java.util.List<RoleResponse.Ref> customRoles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Just enough to show a person next to something (name, avatar, role). For responses that
     * people outside user management can read — a company's main engineer is visible to that
     * company's own customers, who have no business with the engineer's phone, email or settings.
     */
    public static UserResponse publicRef(User u) {
        UserResponse r = new UserResponse();
        r.id = u.getId();
        r.firstName = u.getFirstName();
        r.lastName = u.getLastName();
        r.role = u.getRole();
        r.photoUrl = u.getPhotoId() == null ? null : "/api/media/" + u.getPhotoId();
        return r;
    }

    public static UserResponse from(User u) {
        UserResponse r = new UserResponse();
        r.id = u.getId();
        r.userCode = u.getUserCode();
        r.email = u.getEmail();
        r.firstName = u.getFirstName();
        r.lastName = u.getLastName();
        r.phone = u.getPhone();
        r.role = u.getRole();
        r.status = u.getStatus();
        if (u.getCompany() != null) {
            r.companyId = u.getCompany().getId();
            r.companyName = u.getCompany().getName();
        }
        r.photoUrl = u.getPhotoId() == null ? null : "/api/media/" + u.getPhotoId();
        r.notifications = NotificationPreferences.from(u);
        r.mustChangePassword = u.isMustChangePassword();
        r.createdAt = u.getCreatedAt();
        r.updatedAt = u.getUpdatedAt();
        return r;
    }

    public Long getId() { return id; }
    public String getUserCode() { return userCode; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPhone() { return phone; }
    public Roles getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public Long getCompanyId() { return companyId; }
    public String getCompanyName() { return companyName; }
    public String getPhotoUrl() { return photoUrl; }
    public java.util.List<String> getCompanyFeatures() { return companyFeatures; }
    public void setCompanyFeatures(java.util.List<String> companyFeatures) { this.companyFeatures = companyFeatures; }

    public NotificationPreferences getNotifications() { return notifications; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public java.util.List<CompanyRef> getAssignedCompanies() { return assignedCompanies; }
    public void setAssignedCompanies(java.util.List<CompanyRef> assignedCompanies) { this.assignedCompanies = assignedCompanies; }
    public java.util.List<RoleResponse.Ref> getCustomRoles() { return customRoles; }
    public void setCustomRoles(java.util.List<RoleResponse.Ref> customRoles) { this.customRoles = customRoles; }

    public record CompanyRef(Long id, String name) {}
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
