-- ============================================================
-- V2__create_subscription_events.sql
-- AI BOS — Subscription events audit log
-- ============================================================

CREATE TABLE IF NOT EXISTS subscription_events (
                                                   id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type        VARCHAR(30) NOT NULL,
    from_tier         VARCHAR(20) NOT NULL,
    to_tier           VARCHAR(20) NOT NULL,
    stripe_event_id   VARCHAR(255),
    stripe_invoice_id VARCHAR(255),
    amount_cents      INTEGER,
    currency          VARCHAR(3)    DEFAULT 'USD',
    occurred_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    recorded_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata          JSONB
    );

-- ── Constraints ───────────────────────────────────────────────────────────────

ALTER TABLE subscription_events
    ADD CONSTRAINT chk_se_event_type CHECK (
        event_type IN ('UPGRADE','RENEWAL','DOWNGRADE','CANCELLATION','TRIAL_START','TRIAL_END')
        );

ALTER TABLE subscription_events
    ADD CONSTRAINT chk_se_from_tier CHECK (from_tier IN ('FREE','PRO','ENTERPRISE'));

ALTER TABLE subscription_events
    ADD CONSTRAINT chk_se_to_tier   CHECK (to_tier   IN ('FREE','PRO','ENTERPRISE'));

ALTER TABLE subscription_events
    ADD CONSTRAINT chk_se_amount    CHECK (amount_cents IS NULL OR amount_cents >= 0);

ALTER TABLE subscription_events
    ADD CONSTRAINT uk_subscription_events_stripe_event_id
        UNIQUE (stripe_event_id);

-- ── Indexes ───────────────────────────────────────────────────────────────────

-- User billing history in chronological order
CREATE INDEX idx_se_user_id
    ON subscription_events (user_id, occurred_at DESC);

-- Idempotency: prevent duplicate webhook processing
-- Partial: NULL stripe_event_id rows (free-tier events) excluded
CREATE UNIQUE INDEX idx_se_stripe_event
    ON subscription_events (stripe_event_id)
    WHERE stripe_event_id IS NOT NULL;

-- Revenue analytics by date range
CREATE INDEX idx_se_occurred_at
    ON subscription_events (occurred_at);

-- JSONB GIN index for metadata queries (debugging, admin search)
CREATE INDEX idx_se_metadata
    ON subscription_events USING GIN (metadata)
    WHERE metadata IS NOT NULL;

COMMENT ON TABLE subscription_events IS
    'Immutable audit log. Every tier change writes one row. Never update or delete rows.';
COMMENT ON COLUMN subscription_events.occurred_at IS
    'When the event occurred per Stripe payload — NOT webhook delivery time.';
COMMENT ON COLUMN subscription_events.metadata IS
    'Raw Stripe event JSON. Encrypted at rest by database-level encryption.';