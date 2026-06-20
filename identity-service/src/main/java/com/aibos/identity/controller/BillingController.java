package com.aibos.identity.controller;

import com.aibos.identity.dto.request.CreateCheckoutSessionRequest;
import com.aibos.identity.dto.response.CheckoutSessionResponse;
import com.aibos.identity.entity.User;
import com.aibos.identity.security.AibosUserDetails;
import com.aibos.identity.service.StripeService;
import com.aibos.identity.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final StripeService stripeService;
    private final UserService userService;

    /**
     * Creates a Stripe Checkout Session.
     * Frontend redirects user to the returned checkout_url.
     */
    @PostMapping("/checkout")
    public CheckoutSessionResponse createCheckout(
            @AuthenticationPrincipal AibosUserDetails principal,
            @Valid @RequestBody CreateCheckoutSessionRequest request
    ) {
        User user = userService.findOrThrow(principal.getUserId());
        return stripeService.createCheckoutSession(user, request);
    }
}