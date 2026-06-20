package com.aibos.identity.repository;

import com.aibos.identity.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Primary lookup on each token use (uses idx_rt_token_hash). */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** List active sessions for security dashboard (uses partial index idx_rt_user_id). */
    List<RefreshToken> findByUserIdAndRevokedFalse(UUID userId);

    /** Revoke all sessions for a user (logout-all). */
    @Modifying
    @Query("""
            UPDATE RefreshToken rt
            SET rt.revoked = true, rt.revokedAt = :now
            WHERE rt.user.id = :userId AND rt.revoked = false
            """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Nightly cleanup:
     * - Expired tokens (hard delete)
     * - Revoked tokens older than 30 days (audit window)
     */
    @Modifying
    @Query("""
            DELETE FROM RefreshToken rt
            WHERE rt.expiresAt < :now
               OR (rt.revoked = true AND rt.revokedAt < :auditCutoff)
            """)
    int deleteExpiredAndOldRevoked(
            @Param("now") Instant now,
            @Param("auditCutoff") Instant auditCutoff);
}
