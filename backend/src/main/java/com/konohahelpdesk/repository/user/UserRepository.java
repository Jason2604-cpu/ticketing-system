package com.konohahelpdesk.repository.user;


import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
public interface UserRepository extends JpaRepository<User, Long> {
    
    // ── Login ────────────────────────────────────────────────────
    // Find by email but ONLY if not soft deleted
    Optional<User> findByEmailAndStatusNot(String email, UserStatus status);
    Optional<User> findByEmailIgnoreCaseAndStatusNot(String email, UserStatus status);
    // Usage: findByEmailAndStatusNot(email, UserStatus.IS_DELETED)

    // ── Single lookups ───────────────────────────────────────────
    Optional<User> findByUserCode(String userCode);

    // Find by ID only if not deleted — used for profile/edit pages
    Optional<User> findByIdAndStatusNot(Long id, UserStatus status);

    // ── List queries — always exclude IS_DELETED ─────────────────

    // All users by role that are NOT deleted
    // Used for Engineer Management page listing
    List<User> findByRoleAndStatusNot(Roles role, UserStatus status);

    // All users by specific status
    // e.g. findByStatus(UserStatus.INVITED) → pending invites
    List<User> findByStatus(UserStatus status);

    // Engineers by role AND status
    // e.g. ENGINEER + ACTIVE only
    List<User> findByRoleAndStatus(Roles role, UserStatus status);

    // All engineers under a company that are not deleted
    List<User> findByCompanyIdAndStatusNot(Long companyId, UserStatus status);

    /** e.g. a company's ACTIVE Customer Admins — who the monthly automation summary is addressed to. */
    List<User> findByCompanyIdAndRoleAndStatus(Long companyId, Roles role, UserStatus status);

    // ── Existence checks ─────────────────────────────────────────

    // Check duplicate email — ignore deleted accounts
    // e.g. a deleted user's email can be reused
    boolean existsByEmailAndStatusNot(String email, UserStatus status);
    boolean existsByEmailIgnoreCaseAndStatusNot(String email, UserStatus status);

    // userCode uniqueness (soft-deleted users keep their code)
    boolean existsByUserCode(String userCode);

    // Check duplicate email excluding current user (for updates)
    boolean existsByEmailAndIdNotAndStatusNot(String email, Long id, UserStatus status);

    // ── Counting — for userCode generation ───────────────────────

    // Count only non-deleted engineers
    long countByRoleAndStatusNot(Roles role, UserStatus status);

    // Subscription quota: how many seats a company is using
    long countByCompanyIdAndStatusNot(Long companyId, UserStatus status);

    /**
     * Active-user counts for many companies in one query: [companyId, count].
     * The companies list needs a seat count per row; asking per row was N queries on a list that
     * refetches every 15 seconds.
     */
    @Query("""
        select u.company.id, count(u) from User u
        where u.company.id in :companyIds and u.status <> :excluded
        group by u.company.id
        """)
    List<Object[]> countActiveByCompanyIds(@Param("companyIds") java.util.Collection<Long> companyIds,
                                           @Param("excluded") UserStatus excluded);

    /**
     * The company-admin of each company, for the companies list. Replaces loading EVERY user in
     * the system and filtering in memory, which is what that list used to do on every poll.
     */
    List<User> findByRoleAndStatusNotAndCompanyIdIn(Roles role, UserStatus status, java.util.Collection<Long> companyIds);

    /**
     * Everyone a privilege applies to, for telling them about work waiting on it: active Admins
     * (who hold every privilege) plus active Engineer / Default employees granted a custom role
     * that carries it — the same rule JwtCookieFilter applies when it builds the PRIV_* authorities.
     */
    @Query("""
        select u from User u
        where u.status = com.konohahelpdesk.model.user.UserStatus.ACTIVE
          and (u.role = com.konohahelpdesk.model.user.Roles.ADMIN
               or (u.role in (com.konohahelpdesk.model.user.Roles.ENGINEER, com.konohahelpdesk.model.user.Roles.STAFF)
                   and exists (select 1 from UserRole ur join ur.role r join r.privileges p
                               where ur.user = u and r.custom = true and p = :privilege)))
        order by u.id
        """)
    List<User> findActiveHolders(@Param("privilege") com.konohahelpdesk.model.rbac.Privilege privilege);

    // All non-deleted users (list endpoint) — avoids loading everything then filtering in memory
    List<User> findByStatusNot(UserStatus status);

    // ── Soft delete helper ───────────────────────────────────────

    // Custom query — update status to IS_DELETED instead of deleting
    // @Query lets you write your own JPQL (Java version of SQL)
    @Query("UPDATE User u SET u.status = 'IS_DELETED', u.updatedAt = CURRENT_TIMESTAMP WHERE u.id = :id")
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    void softDeleteById(Long id);


}
