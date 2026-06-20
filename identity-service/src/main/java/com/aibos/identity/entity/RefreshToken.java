package com.aibos.identity.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Server-side refresh token state.
 * SECURITY: Only the SHA-256 hash of the plain token is ever stored here.
 * The plain token is returned to the client once and never persisted.
 * On every use, the old token is revoked and a new one issued (rotation).
 */
@Entity
@Table(name = "refresh_tokens")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256(plain_token). Never store the plain token. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    /** Browser/device user-agent hint for security dashboard display. */
    @Column(name = "device_hint", length = 200)
    private String deviceHint;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /** Hard TTL: 7 days from issuance. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** TRUE on use (rotation) or explicit logout. */
    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    // ── Domain helpers ──────────────────────────────────────────────────────

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !revoked && !isExpired();
    }

    public void revoke() {
        this.revoked = true;
        this.revokedAt = Instant.now();
    }
}
