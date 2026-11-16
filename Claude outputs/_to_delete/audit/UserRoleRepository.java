package com.konohahelpdesk.repository.rbac;

import com.konohahelpdesk.model.rbac.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findByUserId(Long userId);
    /**
     * Scoped variants: the fixed-enum additive grants (ADMIN/ENGINEER, role.custom = false) and the
     * admin-created custom-role grants (role.custom = true) are replaced independently — saving one
     * set must never wipe out the other.
     */
    List<UserRole> findByUserIdAndRole_Custom(Long userId, boolean custom);

    List<UserRole> findByUserIdInAndRole_Custom(Collection<Long> userIds, boolean custom);

    void deleteByUserIdAndRole_Custom(Long userId, boolean custom);

    void deleteByRoleId(Long roleId);}
