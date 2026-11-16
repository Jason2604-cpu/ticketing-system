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
     * The roles granted to a user, with their privileges, in one query — what JwtCookieFilter needs
     * on EVERY request. findByUserId would also reload the user (and its company graph) per row.
     */
    @org.springframework.data.jpa.repository.Query(
        "select r from UserRole ur join ur.role r left join fetch r.privileges where ur.user.id = :userId")
    List<com.konohahelpdesk.model.rbac.Role> findRolesWithPrivilegesByUserId(
        @org.springframework.data.repository.query.Param("userId") Long userId);
    /**
     * Scoped variants: the fixed-enum additive grants (ADMIN/ENGINEER, role.custom = false) and the
     * admin-created custom-role grants (role.custom = true) are replaced independently — saving one
     * set must never wipe out the other.
     */
    List<UserRole> findByUserIdAndRole_Custom(Long userId, boolean custom);

    List<UserRole> findByUserIdInAndRole_Custom(Collection<Long> userIds, boolean custom);

    void deleteByUserIdAndRole_Custom(Long userId, boolean custom);

    void deleteByRoleId(Long roleId);}
