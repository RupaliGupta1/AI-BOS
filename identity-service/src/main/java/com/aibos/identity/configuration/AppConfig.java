package com.aibos.identity.configuration;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables:
 * - JPA Auditing (createdAt / updatedAt auto-populated on BaseEntity)
 * - Scheduling (TierExpirationScheduler)
 * - ConfigurationProperties scanning (JwtProperties, etc.)
 */
@Configuration
@EnableJpaAuditing
@EnableScheduling
@ConfigurationPropertiesScan("com.aibos")
public class AppConfig {

}
