package com.aibos.identity.service;

import com.aibos.identity.configuration.StripeProperties;
import com.aibos.identity.dto.request.CreateCheckoutSessionRequest;
import com.aibos.identity.dto.response.CheckoutSessionResponse;
import com.aibos.identity.entity.User;
import com.aibos.identity.enums.Tier;
import com.aibos.identity.exception.custom.StripeWebhookException;
import com.aibos.identity.repository.UserRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeService {

    private final StripeProperties stripeProperties;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;

    @Value("${aibos.frontend.base-url}")
    private String frontendBaseUrl;

    // ── Checkout Session creation ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CheckoutSessionResponse createCheckoutSession(
            User user,
            CreateCheckoutSessionRequest request
    ) {
        Tier requestedTier = Tier.valueOf(request.tier().toUpperCase());

        if (requestedTier == Tier.FREE) {
            throw new IllegalArgumentException("Cannot create checkout session for FREE tier");
        }

        String priceId = resolvePriceId(requestedTier);

        try {
            SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSuccessUrl(frontendBaseUrl + "/billing/success?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(frontendBaseUrl + "/billing/cancel")
                    .putMetadata("tier", requestedTier.name())
                    .putMetadata("user_id", user.getId().toString())
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setPrice(priceId)
                                    .setQuantity(1L)
                                    .build()
                    );

            if (user.getStripeCustomerId() != null) {
                paramsBuilder.setCustomer(user.getStripeCustomerId());
            } else {
                paramsBuilder.setCustomerEmail(user.getEmail());
            }

            Session session = Session.create(paramsBuilder.build());

            // DIAGNOSTIC: confirm metadata was actually attached at creation time
            log.info("Checkout session created: id={} user={} tier={} metadata={}",
                    session.getId(), user.getId(), requestedTier, session.getMetadata());

            return new CheckoutSessionResponse(session.getUrl(), session.getId());

        } catch (StripeException e) {
            log.error("Stripe checkout session creation failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create checkout session: " + e.getMessage(), e);
        }
    }

    // ── Webhook handling ──────────────────────────────────────────────────────

    public void handleWebhook(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader,
                    stripeProperties.webhookSecret());
        } catch (SignatureVerificationException e) {
            log.error("Stripe signature verification FAILED — check STRIPE_WEBHOOK_SECRET matches the CLI output", e);
            throw new StripeWebhookException("Invalid Stripe signature");
        }

        // DIAGNOSTIC: this MUST appear in your logs for every Stripe event.
        // If it never appears, the webhook isn't reaching this server at all.
        log.info("=== STRIPE WEBHOOK RECEIVED === type={} id={}", event.getType(), event.getId());

        try {
            switch (event.getType()) {
                case "checkout.session.completed" -> handleCheckoutCompleted(event);
                case "invoice.paid"                -> handleInvoicePaid(event);
                case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
                case "invoice.payment_failed"      -> handlePaymentFailed(event);
                default -> log.info("Unhandled Stripe event type: {}", event.getType());
            }
            log.info("=== WEBHOOK PROCESSED SUCCESSFULLY === id={}", event.getId());
        } catch (Exception e) {
            // DIAGNOSTIC: catch-all so we ALWAYS see the real error, not a silent 500
            log.error("=== WEBHOOK PROCESSING FAILED === id={} type={} error={}",
                    event.getId(), event.getType(), e.getMessage(), e);
            throw e;
        }
    }

    // ── Private webhook handlers ──────────────────────────────────────────────

    private void handleCheckoutCompleted(Event event) {
        Session session = (Session) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new StripeWebhookException(
                        "Could not deserialize Session object from event " + event.getId() +
                                " — possible Stripe API version mismatch"));

        log.info("checkout.session.completed: session_id={} metadata={} customer={} subscription={}",
                session.getId(), session.getMetadata(), session.getCustomer(), session.getSubscription());

        String userId   = session.getMetadata() != null ? session.getMetadata().get("user_id") : null;
        String tierName = session.getMetadata() != null ? session.getMetadata().get("tier") : null;

        if (userId == null || tierName == null) {
            log.error("MISSING METADATA on session {}. Full metadata map: {}. " +
                            "This means metadata was not attached at session creation, " +
                            "or this event predates a code change.",
                    session.getId(), session.getMetadata());
            throw new StripeWebhookException(
                    "Missing user_id/tier metadata in checkout session " + session.getId());
        }

        User user = findUserById(UUID.fromString(userId));
        Tier newTier = Tier.valueOf(tierName);
        LocalDate paymentDate = toLocalDate(event.getCreated());

        log.info("Upgrading user {} from {} to {} (payment date={})",
                user.getId(), user.getTier(), newTier, paymentDate);

        subscriptionService.upgrade(
                user,
                newTier,
                paymentDate,
                session.getCustomer(),
                session.getSubscription(),
                event.getId(),
                null,
                extractAmountCents(session),
                Map.of("stripe_event_type", event.getType(),
                        "session_id", session.getId())
        );

        log.info("Upgrade complete for user {}", user.getId());
    }

    private void handleInvoicePaid(Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow();

        if ("subscription_create".equals(invoice.getBillingReason())) {
            log.info("Skipping invoice.paid (billing_reason=subscription_create) — " +
                    "already handled by checkout.session.completed");
            return;
        }

        User user = findUserByStripeCustomerId(invoice.getCustomer());

        subscriptionService.renew(
                user,
                event.getId(),
                invoice.getId(),
                (int) (long) invoice.getAmountPaid(),
                Map.of("stripe_event_type", event.getType(),
                        "invoice_id", invoice.getId())
        );
    }

    private void handleSubscriptionDeleted(Event event) {
        Subscription sub = (Subscription) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow();

        User user = findUserByStripeCustomerId(sub.getCustomer());
        subscriptionService.downgradeToFree(
                user, event.getId(), Map.of("stripe_event_type", event.getType()));
    }

    private void handlePaymentFailed(Event event) {

        Optional<Invoice> optionalInvoice = event.getDataObjectDeserializer()
                .getObject()
                .map(obj -> (Invoice) obj);

        if (optionalInvoice.isEmpty()) {
            log.error("Failed to deserialize Invoice from Stripe event id={}", event.getId());
            return; // IMPORTANT: don't crash webhook
        }

        Invoice invoice = optionalInvoice.get();

        log.warn("Payment FAILED for customer {} invoice {} — grace period applies",
                invoice.getCustomer(), invoice.getId());
    }
    // ── Private helpers ───────────────────────────────────────────────────────

    private User findUserById(UUID userId) {
        return userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new StripeWebhookException(
                        "User not found for id: " + userId));
    }

    private User findUserByStripeCustomerId(String customerId) {
        return userRepository.findByStripeCustomerIdAndDeletedAtIsNull(customerId)
                .orElseThrow(() -> new StripeWebhookException(
                        "User not found for Stripe customer: " + customerId));
    }

    private String resolvePriceId(Tier tier) {
        Map<String, String> priceIds = stripeProperties.priceIds();
        String key = tier.name().toLowerCase();
        String priceId = priceIds != null ? priceIds.get(key) : null;
        if (priceId == null || priceId.isBlank() || priceId.contains("placeholder")) {
            throw new IllegalStateException(
                    "Stripe price ID not configured for tier: " + tier +
                            ". Set STRIPE_PRICE_" + tier.name() + " environment variable.");
        }
        return priceId;
    }

    private Integer extractAmountCents(Session session) {
        return session.getAmountTotal() != null
                ? session.getAmountTotal().intValue()
                : null;
    }

    private LocalDate toLocalDate(Long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds)
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
    }
}