package com.aibos.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CheckoutSessionResponse(
        @JsonProperty("checkout_url") String checkoutUrl,
        @JsonProperty("session_id") String sessionId
) {}