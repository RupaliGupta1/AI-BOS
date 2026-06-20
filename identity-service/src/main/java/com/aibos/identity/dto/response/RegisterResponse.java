package com.aibos.identity.dto.response;

import com.aibos.identity.enums.Tier;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record RegisterResponse(
        UUID id,
        String email,
        String name,
        Tier tier,
        @JsonProperty("email_verified") boolean emailVerified,
        String message
) {}

