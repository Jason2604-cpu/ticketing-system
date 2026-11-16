package com.konohahelpdesk.repository.user;

import com.konohahelpdesk.model.user.AccountToken;
import com.konohahelpdesk.model.user.AccountTokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {

    Optional<AccountToken> findByTokenHashAndType(String tokenHash, AccountTokenType type);

    /** Invalidate every outstanding token of a type for a user (issuing a new one, or after use). */
    @Modifying
    @Query("UPDATE AccountToken t SET t.usedAt = :now WHERE t.user.id = :userId AND t.type = :type AND t.usedAt IS NULL")
    void invalidateAll(Long userId, AccountTokenType type, LocalDateTime now);

    @Modifying
    @Query("DELETE FROM AccountToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(LocalDateTime cutoff);
}
