package com.aibos.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Returned on login and token refresh.
 * accessToken: short-lived JWT (15 min) for API calls.
 * refreshToken: long-lived opaque token (7 days) for rotation — stored client-side in httpOnly cookie or secure storage.
 */
public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresInSeconds
) {
    public static TokenResponse of(String accessToken, String refreshToken) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", 900L);
    }
}


