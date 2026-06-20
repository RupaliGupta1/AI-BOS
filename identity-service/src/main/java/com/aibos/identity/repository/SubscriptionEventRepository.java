package com.aibos.identity.repository;

import com.aibos.identity.entity.SubscriptionEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionEventRepository extends JpaRepository<SubscriptionEvent, UUID> {

    /** Idempotency check — prevent duplicate Stripe webhook processing. */
    boolean existsByStripeEventId(String stripeEventId);

    /** Fetch tier history for a user, most recent first (uses idx_se_user_id). */
    List<SubscriptionEvent> findByUserIdOrderByOccurredAtDesc(UUID userId);

    Page<SubscriptionEvent> findByUserIdOrderByOccurredAtDesc(UUID userId, Pageable pageable);

    /** Revenue analytics by date range (uses idx_se_occurred_at). */
    @Query("""
            SELECT se FROM SubscriptionEvent se
            WHERE se.occurredAt BETWEEN :from AND :to
            ORDER BY se.occurredAt DESC
            """)
    List<SubscriptionEvent> findByOccurredAtBetween(
            @Param("from") Instant from,
            @Param("to") Instant to);

    /** Sum of revenue in a period for analytics. */
    @Query("""
            SELECT COALESCE(SUM(se.amountCents), 0)
            FROM SubscriptionEvent se
            WHERE se.occurredAt BETWEEN :from AND :to
              AND se.amountCents IS NOT NULL
            """)
    long sumRevenueInPeriod(@Param("from") Instant from, @Param("to") Instant to);

    Optional<SubscriptionEvent> findByStripeEventId(String stripeEventId);
}