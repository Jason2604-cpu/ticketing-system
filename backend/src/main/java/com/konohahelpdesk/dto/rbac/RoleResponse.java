package com.konohahelpdesk.dto.rbac;

import com.konohahelpdesk.model.rbac.Privilege;
import com.konohahelpdesk.model.rbac.Role;

import java.util.List;

public class RoleResponse {
    private Long id;
    private String name;
    private String description;
    private boolean custom;
    private List<Privilege> privileges;

    public static RoleResponse from(Role role) {
        RoleResponse r = new RoleResponse();
        r.id = role.getId();
        r.name = role.getName();
        r.description = role.getDescription();
        r.custom = role.isCustom();
        r.privileges = role.getPrivileges().stream().sorted().toList();
        return r;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isCustom() { return custom; }
    public List<Privilege> getPrivileges() { return privileges; }

    /** Slim reference used inline on a user (e.g. `customRoles`) — no need to repeat every field. */
    public record Ref(Long id, String name, List<Privilege> privileges) {
        public static Ref from(Role role) {
            return new Ref(role.getId(), role.getName(), role.getPrivileges().stream().sorted().toList());
        }
    }
}
