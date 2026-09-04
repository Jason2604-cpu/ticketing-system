package com.konohahelpdesk.dto.rbac;

import com.konohahelpdesk.model.rbac.Privilege;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Create or update a custom role. System roles (Admin/Engineer/Company Admin/Customer) never go through this. */
public class RoleRequest {

    @NotBlank
    @Size(max = 60)
    private String name;

    @Size(max = 200)
    private String description;

    private Set<Privilege> privileges;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Set<Privilege> getPrivileges() { return privileges; }
    public void setPrivileges(Set<Privilege> privileges) { this.privileges = privileges; }
}
