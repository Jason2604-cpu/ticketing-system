package com.konohahelpdesk.repository;


import com.konohahelpdesk.model.User;
import com.konohahelpdesk.model.Roles;
import com.konohahelpdesk.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
public interface UserRepository extends JpaRepository<User, Long> {
    
    // ── Login ────────────────────────────────────────────────────
    // Find by email but ONLY if not soft deleted
    Optional<User> findByEmailAndStatusNot(String email, UserStatus status);
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

    // ── Existence checks ─────────────────────────────────────────

    // Check duplicate email — ignore deleted accounts
    // e.g. a deleted user's email can be reused
    boolean existsByEmailAndStatusNot(String email, UserStatus status);

    // Check duplicate email excluding current user (for updates)
    boolean existsByEmailAndIdNotAndStatusNot(String email, Long id, UserStatus status);

    // ── Counting — for userCode generation ───────────────────────

    // Count only non-deleted engineers
    long countByRoleAndStatusNot(Roles role, UserStatus status);

    // ── Soft delete helper ───────────────────────────────────────

    // Custom query — update status to IS_DELETED instead of deleting
    // @Query lets you write your own JPQL (Java version of SQL)
    @Query("UPDATE User u SET u.status = 'IS_DELETED', u.updatedAt = CURRENT_TIMESTAMP WHERE u.id = :id")
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    void softDeleteById(Long id);


}
