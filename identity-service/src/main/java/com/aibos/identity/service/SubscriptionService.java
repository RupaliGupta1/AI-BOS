package com.aibos.identity.service;

import com.aibos.identity.dto.response.SubscriptionEventResponse;
import com.aibos.identity.entity.SubscriptionEvent;
import com.aibos.identity.entity.User;
import com.aibos.identity.enums.SubscriptionEventType;
import com.aibos.identity.enums.Tier;
import com.aibos.identity.repository.SubscriptionEventRepository;
import com.aibos.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Handles all tier transitions.
 *
 * Key invariants:
 * 1. tier_start_date comes from Stripe payment timestamp, NOT server clock.
 * 2. Renewal extends from currentExpiry, NOT from now(), to prevent billing drift.
 * 3. Every transition writes an immutable SubscriptionEvent audit row.
 * 4. Stripe event IDs are idempotency keys — duplicate webhooks are ignored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final UserRepository userRepository;
    private final SubscriptionEventRepository eventRepository;

    // ── Upgrade ──────────────────────────────────────────────────────────────

    @Transactional
    public void upgrade(
            User user,
            Tier newTier,
            LocalDate paymentDate,     // from Stripe payload — not LocalDate.now()
            String stripeCustomerId,
            String stripeSubscriptionId,
            String stripeEventId,
            String stripeInvoiceId,
            Integer amountCents,
            Map<String, Object> rawPayload
    ) {
        if (eventRepository.existsByStripeEventId(stripeEventId)) {
            log.warn("Duplicate Stripe event ignored: {}", stripeEventId);
            return; // idempotent
        }

        Tier fromTier = user.getTier();

        user.setTier(newTier);
        user.setTierStartDate(paymentDate);
        user.setTierExpiresAt(paymentDate.plusMonths(1));
        user.setStripeCustomerId(stripeCustomerId);
        user.setStripeSubscriptionId(stripeSubscriptionId);
        userRepository.save(user);

        recordEvent(user, SubscriptionEventType.UPGRADE, fromTier, newTier,
                stripeEventId, stripeInvoiceId, amountCents, rawPayload);
        log.info("User {} upgraded {} -> {} (stripe_event={})", user.getId(), fromTier, newTier, stripeEventId);
    }

    // ── Renewal ───────────────────────────────────────────────────────────────

    @Transactional
    public void renew(
            User user,
            String stripeEventId,
            String stripeInvoiceId,
            Integer amountCents,
            Map<String, Object> rawPayload
    ) {
        if (eventRepository.existsByStripeEventId(stripeEventId)) {
            log.warn("Duplicate renewal event ignored: {}", stripeEventId);
            return;
        }

        Tier currentTier = user.getTier();
        LocalDate currentExpiry = user.getTierExpiresAt();

        // CRITICAL: extend from currentExpiry, not LocalDate.now()
        // This prevents billing drift where failed/delayed webhooks cause short billing periods
        LocalDate newExpiry = (currentExpiry != null)
                ? currentExpiry.plusMonths(1)
                : LocalDate.now().plusMonths(1);

        user.setTierExpiresAt(newExpiry);
        userRepository.save(user);

        recordEvent(user, SubscriptionEventType.RENEWAL, currentTier, currentTier,
                stripeEventId, stripeInvoiceId, amountCents, rawPayload);
        log.info("User {} renewed {} until {} (stripe_event={})", user.getId(), currentTier, newExpiry, stripeEventId);
    }

    // ── Downgrade / Cancellation ──────────────────────────────────────────────

    @Transactional
    public void downgradeToFree(User user, String stripeEventId, Map<String, Object> rawPayload) {
        if (stripeEventId != null && eventRepository.existsByStripeEventId(stripeEventId)) {
            log.warn("Duplicate cancellation event ignored: {}", stripeEventId);
            return;
        }

        Tier fromTier = user.getTier();
        user.downgradeToFree(); // sets tier=FREE, tierExpiresAt=null, stripeSubscriptionId=null
        userRepository.save(user);

        recordEvent(user, SubscriptionEventType.DOWNGRADE, fromTier, Tier.FREE,
                stripeEventId, null, null, rawPayload);
        log.info("User {} downgraded to FREE (stripe_event={})", user.getId(), stripeEventId);
    }

    // ── History ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<SubscriptionEventResponse> getHistory(UUID userId, Pageable pageable) {
        return eventRepository.findByUserIdOrderByOccurredAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void recordEvent(
            User user,
            SubscriptionEventType type,
            Tier from,
            Tier to,
            String stripeEventId,
            String stripeInvoiceId,
            Integer amountCents,
            Map<String, Object> metadata
    ) {
        SubscriptionEvent event = SubscriptionEvent.builder()
                .user(user)
                .eventType(type)
                .fromTier(from)
                .toTier(to)
                .stripeEventId(stripeEventId)
                .stripeInvoiceId(stripeInvoiceId)
                .amountCents(amountCents)
                .occurredAt(Instant.now())
                .metadata(metadata)
                .build();
        eventRepository.save(event);
    }

    private SubscriptionEventResponse toResponse(SubscriptionEvent e) {
        return new SubscriptionEventResponse(
                e.getId(), e.getEventType(), e.getFromTier(), e.getToTier(),
                e.getAmountCents(), e.getCurrency(), e.getOccurredAt()
        );
    }
}

