package com.konohahelpdesk.config;

import com.konohahelpdesk.model.rbac.Role;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.repository.rbac.RoleRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures a {@link Role} row exists for every {@link Roles} enum constant. Runs on every startup;
 * idempotent (upserts by name only when missing).
 */
@Component
public class RoleSeeder implements ApplicationRunner {

    private final RoleRepository roleRepository;

    public RoleSeeder(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Roles r : Roles.values()) {
            if (roleRepository.findByName(r.name()).isEmpty()) {
                Role role = new Role();
                role.setName(r.name());
                roleRepository.save(role);
            }
        }
    }
}
