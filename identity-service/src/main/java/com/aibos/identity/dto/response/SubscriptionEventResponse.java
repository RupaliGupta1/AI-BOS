package com.aibos.identity.dto.response;

import com.aibos.identity.enums.SubscriptionEventType;
import com.aibos.identity.enums.Tier;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionEventResponse(
        UUID id,
        @JsonProperty("event_type") SubscriptionEventType eventType,
        @JsonProperty("from_tier") Tier fromTier,
        @JsonProperty("to_tier") Tier toTier,
        @JsonProperty("amount_cents") Integer amountCents,
        String currency,
        @JsonProperty("occurred_at") Instant occurredAt
) {}


