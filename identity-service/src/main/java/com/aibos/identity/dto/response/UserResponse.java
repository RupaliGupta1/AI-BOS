package com.aibos.identity.dto.response;
import com.aibos.identity.enums.Tier;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String name,
        Tier tier,
        @JsonProperty("tier_expires_at") LocalDate tierExpiresAt,
        @JsonProperty("email_verified") boolean emailVerified,
        @JsonProperty("oauth_provider") String oauthProvider
) {}

