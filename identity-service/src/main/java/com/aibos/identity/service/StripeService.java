package com.aibos.identity.service;

import com.aibos.identity.configuration.StripeProperties;
import com.aibos.identity.dto.request.CreateCheckoutSessionRequest;
import com.aibos.identity.dto.response.CheckoutSessionResponse;
import com.aibos.identity.entity.User;
import com.aibos.identity.enums.Tier;
import com.aibos.identity.exception.custom.StripeWebhookException;
import com.aibos.identity.repository.UserRepository;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
        if (request == null || request.tier() == null || request.tier().isBlank()) {
            throw new IllegalArgumentException("tier is required");
        }

        Tier requestedTier;
        try {
            requestedTier = Tier.valueOf(request.tier().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid tier '" + request.tier() + "'. Must be one of: PRO, ENTERPRISE");
        }

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

            if (user.getStripeCustomerId() != null && !user.getStripeCustomerId().isBlank()) {
                paramsBuilder.setCustomer(user.getStripeCustomerId());
            } else {
                paramsBuilder.setCustomerEmail(user.getEmail());
            }

            Session session = Session.create(paramsBuilder.build());

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
        if (payload == null || payload.isBlank()) {
            throw new StripeWebhookException("Empty webhook payload");
        }
        if (sigHeader == null || sigHeader.isBlank()) {
            throw new StripeWebhookException("Missing Stripe-Signature header");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader,
                    stripeProperties.webhookSecret());
        } catch (SignatureVerificationException e) {
            log.error("Stripe signature verification FAILED — check STRIPE_WEBHOOK_SECRET matches the CLI output. " +
                    "Error: {}", e.getMessage());
            throw new StripeWebhookException("Invalid Stripe signature");
        } catch (Exception e) {
            // Catch malformed JSON / unexpected payload shape rather than 500-ing blindly
            log.error("Failed to construct Stripe event from payload: {}", e.getMessage(), e);
            throw new StripeWebhookException("Could not parse Stripe webhook payload: " + e.getMessage());
        }

        log.info("=== STRIPE WEBHOOK RECEIVED === type={} id={} api_version={}",
                event.getType(), event.getId(), event.getApiVersion());

        try {
            switch (event.getType()) {
                case "checkout.session.completed" -> handleCheckoutCompleted(event);
                case "invoice.paid"                -> handleInvoicePaid(event);
                case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
                case "invoice.payment_failed"      -> handlePaymentFailed(event);
                default -> log.info("Unhandled Stripe event type: {}", event.getType());
            }
            log.info("=== WEBHOOK PROCESSED SUCCESSFULLY === id={}", event.getId());
        } catch (StripeWebhookException e) {
            // Known, expected failure modes (missing metadata, user not found, etc.)
            // Re-throw as-is so GlobalExceptionHandler returns 400 → Stripe will retry.
            log.error("=== WEBHOOK PROCESSING FAILED (expected) === id={} type={} error={}",
                    event.getId(), event.getType(), e.getMessage());
            throw e;
        } catch (Exception e) {
            // Unexpected failure — log full stack trace for debugging, still propagate
            // so Stripe retries (returning 200 here would silently lose the event forever).
            log.error("=== WEBHOOK PROCESSING FAILED (unexpected) === id={} type={} error={}",
                    event.getId(), event.getType(), e.getMessage(), e);
            throw e;
        }
    }

    // ── Private webhook handlers ──────────────────────────────────────────────

    private void handleCheckoutCompleted(Event event) {
        JsonObject raw = getRawJson(event);
        Optional<StripeObject> deserialized = event.getDataObjectDeserializer().getObject();

        String sessionId;
        String customerId;
        String subscriptionId;
        Long amountTotal;
        String userId;
        String tierName;
        String paymentStatus;

        if (deserialized.isPresent() && deserialized.get() instanceof Session session) {
            sessionId       = session.getId();
            customerId      = extractId(session.getCustomer());
            subscriptionId  = extractId(session.getSubscription());
            amountTotal     = session.getAmountTotal();
            paymentStatus   = session.getPaymentStatus();
            userId          = session.getMetadata() != null ? session.getMetadata().get("user_id") : null;
            tierName        = session.getMetadata() != null ? session.getMetadata().get("tier") : null;
        } else {
            log.warn("Typed deserialization unavailable for checkout.session.completed (event {}). " +
                    "Using raw JSON.", event.getId());
            sessionId      = getAsString(raw, "id");
            customerId     = extractIdFromJson(raw, "customer");
            subscriptionId = extractIdFromJson(raw, "subscription");
            amountTotal    = getAsLongOrNull(raw, "amount_total");
            paymentStatus  = getAsString(raw, "payment_status");

            JsonObject metadata = (raw.has("metadata") && raw.get("metadata").isJsonObject())
                    ? raw.getAsJsonObject("metadata") : null;
            userId   = metadata != null ? getAsString(metadata, "user_id") : null;
            tierName = metadata != null ? getAsString(metadata, "tier") : null;
        }

        log.info("checkout.session.completed: session_id={} user_id={} tier={} customer={} subscription={} payment_status={}",
                sessionId, userId, tierName, customerId, subscriptionId, paymentStatus);

        // Guard: only process if payment actually completed.
        // "open" or "expired" sessions should not trigger an upgrade.
        if (paymentStatus != null && !"paid".equalsIgnoreCase(paymentStatus)
                && !"no_payment_required".equalsIgnoreCase(paymentStatus)) {
            log.warn("checkout.session.completed received with payment_status={} (not paid) for session {}. Skipping upgrade.",
                    paymentStatus, sessionId);
            return;
        }

        if (userId == null || userId.isBlank()) {
            log.error("MISSING user_id metadata on session {}. Cannot process upgrade.", sessionId);
            throw new StripeWebhookException("Missing user_id metadata in checkout session " + sessionId);
        }
        if (tierName == null || tierName.isBlank()) {
            log.error("MISSING tier metadata on session {}. Cannot process upgrade.", sessionId);
            throw new StripeWebhookException("Missing tier metadata in checkout session " + sessionId);
        }

        UUID userUuid;
        try {
            userUuid = UUID.fromString(userId.trim());
        } catch (IllegalArgumentException e) {
            log.error("user_id metadata '{}' is not a valid UUID on session {}", userId, sessionId);
            throw new StripeWebhookException("Invalid user_id in checkout session metadata: " + userId);
        }

        Tier newTier;
        try {
            newTier = Tier.valueOf(tierName.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("tier metadata '{}' is not a valid Tier on session {}", tierName, sessionId);
            throw new StripeWebhookException("Invalid tier in checkout session metadata: " + tierName);
        }

        User user = findUserById(userUuid);
        LocalDate paymentDate = toLocalDate(event.getCreated());

        log.info("Upgrading user {} from {} to {} (payment date={})",
                user.getId(), user.getTier(), newTier, paymentDate);

        subscriptionService.upgrade(
                user,
                newTier,
                paymentDate,
                customerId,
                subscriptionId,
                event.getId(),
                null,
                amountTotal != null ? amountTotal.intValue() : null,
                Map.of("stripe_event_type", event.getType(), "session_id", sessionId)
        );

        log.info("Upgrade complete for user {} -> {}", user.getId(), newTier);
    }

    private void handleInvoicePaid(Event event) {
        JsonObject raw = getRawJson(event);
        Optional<StripeObject> deserialized = event.getDataObjectDeserializer().getObject();

        String invoiceId;
        String customerId;
        String billingReason;
        Long amountPaid;

        if (deserialized.isPresent() && deserialized.get() instanceof Invoice invoice) {
            invoiceId     = invoice.getId();
            customerId    = extractId(invoice.getCustomer());
            billingReason = invoice.getBillingReason();
            amountPaid    = invoice.getAmountPaid();
        } else {
            log.warn("Typed deserialization unavailable for invoice.paid (event {}). Using raw JSON.",
                    event.getId());
            invoiceId     = getAsString(raw, "id");
            customerId    = extractIdFromJson(raw, "customer");
            billingReason = getAsString(raw, "billing_reason");
            amountPaid    = getAsLongOrNull(raw, "amount_paid");
        }

        if (customerId == null || customerId.isBlank()) {
            log.error("invoice.paid event {} has no customer ID — cannot process renewal.", event.getId());
            throw new StripeWebhookException("Missing customer ID on invoice " + invoiceId);
        }

        if ("subscription_create".equalsIgnoreCase(billingReason)) {
            log.info("Skipping invoice.paid (billing_reason=subscription_create) for invoice {} — " +
                    "first payment already handled by checkout.session.completed", invoiceId);
            return;
        }

        User user;
        try {
            user = findUserByStripeCustomerId(customerId);
        } catch (StripeWebhookException e) {
            // If the user genuinely doesn't have this customer ID yet (e.g. renewal
            // webhook raced ahead of checkout.session.completed), don't crash —
            // log and let Stripe retry; checkout.session.completed should land first.
            log.warn("No user found yet for Stripe customer {} on invoice.paid (event {}). " +
                    "This can happen if checkout.session.completed hasn't been processed yet. " +
                    "Stripe will retry.", customerId, event.getId());
            throw e;
        }

        subscriptionService.renew(
                user,
                event.getId(),
                invoiceId,
                amountPaid != null ? amountPaid.intValue() : null,
                Map.of("stripe_event_type", event.getType(), "invoice_id", invoiceId)
        );
    }

    private void handleSubscriptionDeleted(Event event) {
        JsonObject raw = getRawJson(event);
        Optional<StripeObject> deserialized = event.getDataObjectDeserializer().getObject();

        String customerId;
        if (deserialized.isPresent() && deserialized.get() instanceof Subscription sub) {
            customerId = extractId(sub.getCustomer());
        } else {
            log.warn("Typed deserialization unavailable for customer.subscription.deleted (event {}). Using raw JSON.",
                    event.getId());
            customerId = extractIdFromJson(raw, "customer");
        }

        if (customerId == null || customerId.isBlank()) {
            log.error("customer.subscription.deleted event {} has no customer ID.", event.getId());
            throw new StripeWebhookException("Missing customer ID on subscription deletion event");
        }

        User user = findUserByStripeCustomerId(customerId);
        subscriptionService.downgradeToFree(
                user, event.getId(), Map.of("stripe_event_type", event.getType()));
    }

    private void handlePaymentFailed(Event event) {
        JsonObject raw = getRawJson(event);
        Optional<StripeObject> deserialized = event.getDataObjectDeserializer().getObject();

        String customerId;
        String invoiceId;

        if (deserialized.isPresent() && deserialized.get() instanceof Invoice invoice) {
            customerId = extractId(invoice.getCustomer());
            invoiceId  = invoice.getId();
        } else {
            log.warn("Typed deserialization unavailable for invoice.payment_failed (event {}). Using raw JSON.",
                    event.getId());
            customerId = extractIdFromJson(raw, "customer");
            invoiceId  = getAsString(raw, "id");
        }

        // Informational only — never throw here. Grace period is enforced by
        // TierExpirationScheduler, not by this handler. A user can have many
        // payment_failed retries before the grace period actually expires.
        log.warn("Payment FAILED for customer={} invoice={} — grace period applies, no immediate action taken",
                customerId, invoiceId);
        // TODO: publish PaymentFailedEvent → notify user via email
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Stripe fields like `customer` and `subscription` can be either a plain ID string
     * or an expanded object depending on API version / expand params.
     * This handles both shapes safely via the typed SDK's ExpandableField pattern.
     */
    private String extractId(Object stripeField) {
        if (stripeField == null) return null;
        if (stripeField instanceof String s) return s;
        // Stripe Java SDK ExpandableField objects have getId() via reflection-safe call
        try {
            var method = stripeField.getClass().getMethod("getId");
            Object result = method.invoke(stripeField);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            log.warn("Could not extract ID from Stripe field of type {}: {}",
                    stripeField.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private String extractIdFromJson(JsonObject raw, String fieldName) {
        if (!raw.has(fieldName) || raw.get(fieldName).isJsonNull()) return null;
        JsonElement el = raw.get(fieldName);
        if (el.isJsonPrimitive()) {
            return el.getAsString();
        }
        if (el.isJsonObject() && el.getAsJsonObject().has("id")) {
            return el.getAsJsonObject().get("id").getAsString();
        }
        return null;
    }

    private JsonObject getRawJson(Event event) {
        try {
            String rawJsonString = event.getDataObjectDeserializer().getRawJson();
            return JsonParser.parseString(rawJsonString).getAsJsonObject();
        } catch (Exception e) {
            log.error("Could not parse raw JSON from event {}: {}", event.getId(), e.getMessage());
            throw new StripeWebhookException("Could not parse event data for " + event.getId());
        }
    }

    private String getAsString(JsonObject obj, String key) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : null;
    }

    private Long getAsLongOrNull(JsonObject obj, String key) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsLong() : null;
    }

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

    private LocalDate toLocalDate(Long epochSeconds) {
        if (epochSeconds == null) {
            log.warn("Event has null 'created' timestamp — using current date as fallback");
            return LocalDate.now(ZoneOffset.UTC);
        }
        return Instant.ofEpochSecond(epochSeconds)
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
    }
}