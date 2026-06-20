package com.aibos.identity.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;

@ConfigurationProperties(prefix = "aibos.jwt")
@Validated
public record JwtProperties(
        @NotBlank String secret,
         Duration accessTokenTtl,     // default: PT15M
         Duration refreshTokenTtl     // default: P7D
) {}

