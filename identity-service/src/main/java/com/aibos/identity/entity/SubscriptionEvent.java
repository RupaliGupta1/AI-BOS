package com.aibos.identity.entity;

import com.aibos.identity.enums.SubscriptionEventType;
import com.aibos.identity.enums.Tier;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable audit log entry for every tier change.
 * Rows must NEVER be updated or deleted — they form the billing audit trail.
 * stripe_event_id has a partial unique index to prevent duplicate webhook processing.
 */
@Entity
@Table(name = "subscription_events")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SubscriptionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private SubscriptionEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_tier", nullable = false, length = 20)
    private Tier fromTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_tier", nullable = false, length = 20)
    private Tier toTier;

    /** Stripe idempotency key. Prevents duplicate webhook processing. */
    @Column(name = "stripe_event_id", unique = true, length = 255)
    private String stripeEventId;

    @Column(name = "stripe_invoice_id", length = 255)
    private String stripeInvoiceId;

    /** Amount in cents (e.g. 2900 = $29.00 USD). NULL for free-tier events. */
    @Column(name = "amount_cents")
    private Integer amountCents;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "USD";

    /** When the event actually occurred per Stripe payload — not webhook delivery time. */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** When our server received and recorded the webhook. */
    @Column(name = "recorded_at", nullable = false)
    @Builder.Default
    private Instant recordedAt = Instant.now();

    /** Raw Stripe event payload stored as JSONB for debugging. Encrypted at rest. */
    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}

