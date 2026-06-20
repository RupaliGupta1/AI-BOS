package com.aibos.identity.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

@ConfigurationProperties(prefix = "aibos.stripe")
public record StripeProperties(
        @NotBlank String apiKey,
        @NotBlank String webhookSecret,
        Map<String, String> priceIds   // pro → price_xxx, enterprise → price_yyy
) {}