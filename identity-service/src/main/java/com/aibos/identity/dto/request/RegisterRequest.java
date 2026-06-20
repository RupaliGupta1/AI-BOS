package com.aibos.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration payload.
 * email is normalized to lowercase in UserService before persistence.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 2, max = 200) String name,
        @NotBlank @Size(min = 8, max = 100) String password
) {}


