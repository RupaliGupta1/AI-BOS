package com.aibos.identity.entity;

import com.aibos.identity.enums.Tier;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Central identity aggregate root.
 * Soft-deleted via deletedAt; partial indexes exclude deleted rows from unique constraints.
 */
@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 320)
    private String email;                     // stored lowercase — enforced at service layer

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;              // BCrypt cost-12; NULL for OAuth-only users

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "verify_token", length = 255)
    private String verifyToken;               // SHA-256 token sent in verification email

    @Column(name = "verify_token_expires_at")
    private Instant verifyTokenExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Tier tier = Tier.FREE;

    @Column(name = "tier_start_date")
    private LocalDate tierStartDate;

    @Column(name = "tier_expires_at")
    private LocalDate tierExpiresAt;          // NULL for FREE (never expires)

    @Column(name = "stripe_customer_id", unique = true, length = 255)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", length = 255)
    private String stripeSubscriptionId;

    @Column(name = "oauth_provider", length = 50)
    private String oauthProvider;

    @Column(name = "oauth_provider_id", length = 255)
    private String oauthProviderId;

    @Column(name = "deleted_at")
    private Instant deletedAt;                // non-null = soft-deleted

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SubscriptionEvent> subscriptionEvents = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RefreshToken> refreshTokens = new ArrayList<>();

    // ── Domain helpers ──────────────────────────────────────────────────────

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isTierExpired() {
        return tierExpiresAt != null && LocalDate.now().isAfter(tierExpiresAt);
    }

    public boolean isInGracePeriod(int graceDays) {
        if (tierExpiresAt == null) return false;
        LocalDate today = LocalDate.now();
        return today.isAfter(tierExpiresAt)
                && !today.isAfter(tierExpiresAt.plusDays(graceDays));
    }

    public void downgradeToFree() {
        this.tier = Tier.FREE;
        this.tierExpiresAt = null;
        this.stripeSubscriptionId = null;
    }
}

