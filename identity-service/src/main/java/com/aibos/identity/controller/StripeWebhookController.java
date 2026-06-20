package com.aibos.identity.controller;

import com.aibos.identity.configuration.StripeProperties;
import com.aibos.identity.service.StripeService;
import com.aibos.identity.service.StripeWebhookService;
import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Stripe webhook receiver.
 *
 * IMPORTANT: This endpoint must be excluded from CSRF protection and JWT auth.
 * Stripe signature verification is performed inside StripeWebhookService.
 * The raw request body must be passed unmodified — do NOT use @RequestBody deserialization.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripeService stripeService;
    private final StripeProperties stripeProperties;
    @PostConstruct
    public void initStripe() {
        Stripe.apiKey = stripeProperties.apiKey();
        log.info("Stripe SDK initialized — pinned API version: {}", Stripe.API_VERSION);
    }
    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        stripeService.handleWebhook(payload, sigHeader);
        return ResponseEntity.ok("OK");
    }
}
