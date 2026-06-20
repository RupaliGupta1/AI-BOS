package com.aibos.identity.service;

import com.aibos.identity.entity.User;
import com.aibos.identity.repository.RefreshTokenRepository;
import com.aibos.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Nightly scheduled jobs for tier lifecycle management.
 *
 * Grace period logic:
 *   Day 0 : payment fails → tier_expires_at remains as-is (Stripe retries)
 *   Day 1–3 : user retains paid access (grace period)
 *   After day 3 : this job downgrades to FREE
 *
 * The scheduler checks: tierExpiresAt <= today - GRACE_PERIOD_DAYS
 * i.e. the grace period has fully elapsed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TierExpirationScheduler {

    private static final int GRACE_PERIOD_DAYS = 3;

    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Auto-downgrade users whose paid tier has expired beyond the grace period.
     * Runs nightly at 02:00 UTC.
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    @Transactional
    public void expirePaidTiers() {
        // cutoff = today minus grace period → users expired before this date get downgraded
        LocalDate cutoff = LocalDate.now().minusDays(GRACE_PERIOD_DAYS);
        List<User> expiredUsers = userRepository.findExpiredPaidUsers(cutoff);

        log.info("Tier expiration check: found {} users past grace period", expiredUsers.size());

        for (User user : expiredUsers) {
            try {
                subscriptionService.downgradeToFree(user, null, null);
                log.info("Auto-downgraded user {} (tier_expires_at={})",
                        user.getId(), user.getTierExpiresAt());
            } catch (Exception ex) {
                log.error("Failed to auto-downgrade user {}: {}", user.getId(), ex.getMessage(), ex);
            }
        }
    }

    /**
     * Purge expired and old-revoked refresh tokens.
     * Runs nightly at 03:00 UTC.
     * Revoked tokens are kept 30 days for audit trail before purge.
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @Transactional
    public void purgeExpiredTokens() {
        Instant now = Instant.now();
        Instant auditCutoff = now.minus(java.time.Duration.ofDays(30));

        int deleted = refreshTokenRepository.deleteExpiredAndOldRevoked(now, auditCutoff);
        log.info("Purged {} expired/old-revoked refresh tokens", deleted);
    }
}

