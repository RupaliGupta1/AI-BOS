package com.aibos.identity.repository;

import com.aibos.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /** Primary login lookup — uses partial index idx_users_email. */
    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    /** Stripe webhook handler — uses idx_users_stripe_customer. */
    Optional<User> findByStripeCustomerIdAndDeletedAtIsNull(String stripeCustomerId);

    /** OAuth login matching — uses idx_users_oauth. */
    Optional<User> findByOauthProviderAndOauthProviderIdAndDeletedAtIsNull(
            String oauthProvider, String oauthProviderId);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    /** Nightly job: find paid-tier users whose expiry is at or before the given date. */
    @Query("""
            SELECT u FROM User u
            WHERE u.tier != 'FREE'
              AND u.tierExpiresAt IS NOT NULL
              AND u.tierExpiresAt <= :cutoff
              AND u.deletedAt IS NULL
            """)
    List<User> findExpiredPaidUsers(@Param("cutoff") LocalDate cutoff);

    /** Soft-delete. */
    @Modifying
    @Query("UPDATE User u SET u.deletedAt = CURRENT_TIMESTAMP WHERE u.id = :id")
    int softDelete(@Param("id") UUID id);
}