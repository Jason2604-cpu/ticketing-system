package com.konohahelpdesk.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    /** Engineers only: companies they look after. Populated by the admin listing. */
    private java.util.List<CompanyRef> assignedCompanies;
    /** Additive RBAC: roles granted on top of the primary role, e.g. an ENGINEER also granted ADMIN. */
    private java.util.List<Roles> additionalRoles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
    public NotificationPreferences getNotifications() { return notifications; }
    public java.util.List<CompanyRef> getAssignedCompanies() { return assignedCompanies; }
    public void setAssignedCompanies(java.util.List<CompanyRef> assignedCompanies) { this.assignedCompanies = assignedCompanies; }
    public java.util.List<Roles> getAdditionalRoles() { return additionalRoles; }
    public void setAdditionalRoles(java.util.List<Roles> additionalRoles) { this.additionalRoles = additionalRoles; }

    public record CompanyRef(Long id, String name) {}
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
