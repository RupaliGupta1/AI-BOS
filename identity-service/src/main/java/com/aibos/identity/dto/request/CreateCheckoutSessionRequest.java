package com.aibos.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateCheckoutSessionRequest(
        @NotBlank String tier   // "PRO" or "ENTERPRISE"
) {}