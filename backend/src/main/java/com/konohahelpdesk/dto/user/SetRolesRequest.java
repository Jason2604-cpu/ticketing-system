package com.konohahelpdesk.dto.user;

import com.konohahelpdesk.model.user.Roles;

import java.util.Set;

/**
 * Body of PUT /api/users/{id}/roles — the Users page "Roles" checkboxes for an employee.
 *
 * {@code roles}: ADMIN and/or ENGINEER (Admin includes Engineer); empty = the Default role.
 * {@code customRoleIds}: Role Management roles to grant (ignored for an Admin, who has everything).
 */
public class SetRolesRequest {

    private Set<Roles> roles = Set.of();
    private Set<Long> customRoleIds = Set.of();

    public Set<Roles> getRoles() { return roles; }
    public void setRoles(Set<Roles> roles) { this.roles = roles == null ? Set.of() : roles; }
    public Set<Long> getCustomRoleIds() { return customRoleIds; }
    public void setCustomRoleIds(Set<Long> customRoleIds) { this.customRoleIds = customRoleIds == null ? Set.of() : customRoleIds; }
}
