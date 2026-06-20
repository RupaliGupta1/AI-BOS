package com.aibos.identity.service;
import com.aibos.identity.entity.User;
import com.aibos.identity.enums.Tier;
import com.aibos.identity.exception.custom.StripeWebhookException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Processes Stripe webhook events.
 *
 * Idempotency: stripe_event_id checked before any mutation.
 * Payload timestamps (not server clock) used for billing dates.
 * Raw payload stored in subscription_events.metadata for audit/debugging.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    @Value("${aibos.stripe.webhook-secret}")
    private String webhookSecret;

    private final UserService userService;
    private final SubscriptionService subscriptionService;

    public void handleWebhook(String payload, String sigHeader) {
        com.stripe.model.Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new StripeWebhookException("Invalid Stripe signature");
        }

        log.info("Received Stripe event: {} ({})", event.getType(), event.getId());

        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            case "invoice.paid"               -> handleInvoicePaid(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            case "invoice.payment_failed"     -> handlePaymentFailed(event);
            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    private void handleCheckoutCompleted(com.stripe.model.Event event) {
        Session session = (Session) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new StripeWebhookException("Missing session object"));

        String customerId = session.getCustomer();
        String subscriptionId = session.getSubscription();
        String priceId = session.getMetadata().get("price_id");  // set on checkout creation
        Tier newTier = resolveTierFromPriceId(priceId);

        User user = userService.findOrThrow(findUserByStripeCustomerId(customerId));
        LocalDate paymentDate = toLocalDate(event.getCreated());

        subscriptionService.upgrade(
                user, newTier, paymentDate,
                customerId, subscriptionId,
                event.getId(), null,
                extractAmountCents(session),
                Map.of("stripe_event", event.toJson())
        );
    }

    private void handleInvoicePaid(com.stripe.model.Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                .getObject().orElseThrow();

        String customerId = invoice.getCustomer();
        User user = userService.findOrThrow(findUserByStripeCustomerId(customerId));

        // Distinguish upgrade vs renewal based on billing reason
        if ("subscription_create".equals(invoice.getBillingReason())) {
            // First payment — upgrade already handled in checkout.session.completed, skip.
            return;
        }

        subscriptionService.renew(
                user,
                event.getId(),
                invoice.getId(),
                Math.toIntExact(invoice.getAmountPaid()),
                Map.of("stripe_event", event.toJson())
        );
    }

    private void handleSubscriptionDeleted(com.stripe.model.Event event) {
        Subscription sub = (Subscription) event.getDataObjectDeserializer()
                .getObject().orElseThrow();

        User user = userService.findOrThrow(findUserByStripeCustomerId(sub.getCustomer()));
        subscriptionService.downgradeToFree(user, event.getId(), Map.of("stripe_event", event.toJson()));
    }

    private void handlePaymentFailed(com.stripe.model.Event event) {
        // Payment failed — grace period enforced by TierExpirationScheduler.
        // Log for alerting; do NOT immediately downgrade (3-day grace period applies).
        Invoice invoice = (Invoice) event.getDataObjectDeserializer().getObject().orElseThrow();
        log.warn("Payment failed for customer: {} (invoice={})", invoice.getCustomer(), invoice.getId());
        // TODO: publish PaymentFailedEvent for email notification
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private java.util.UUID findUserByStripeCustomerId(String customerId) {
        // Delegate to UserRepository via UserService
        // In production, UserService exposes findByStripeCustomerId
        throw new UnsupportedOperationException("Inject UserRepository or expose via UserService");
    }

    private Tier resolveTierFromPriceId(String priceId) {
        // Map Stripe price IDs to tiers. Configured in application.yml.
        // e.g. price_pro123 -> PRO, price_ent456 -> ENTERPRISE
        // Placeholder: real impl reads from @ConfigurationProperties
        return Tier.PRO;
    }

    private Integer extractAmountCents(Session session) {
        return session.getAmountTotal() != null ? session.getAmountTotal().intValue() : null;
    }

    private LocalDate toLocalDate(Long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds).atZone(ZoneOffset.UTC).toLocalDate();
    }
}

