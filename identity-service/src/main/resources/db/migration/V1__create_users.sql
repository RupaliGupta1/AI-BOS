-- ============================================================
-- V1__create_users.sql
-- AI BOS — Users table
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
                                     id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    email                   VARCHAR(320)    NOT NULL,
    name                    VARCHAR(200)    NOT NULL,
    password_hash           VARCHAR(255),
    email_verified          BOOLEAN         NOT NULL DEFAULT FALSE,
    verify_token            VARCHAR(255),
    verify_token_expires_at TIMESTAMPTZ,
    tier                    VARCHAR(20)     NOT NULL DEFAULT 'FREE',
    tier_start_date         DATE,
    tier_expires_at         DATE,
    stripe_customer_id      VARCHAR(255),
    stripe_subscription_id  VARCHAR(255),
    oauth_provider          VARCHAR(50),
    oauth_provider_id       VARCHAR(255),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ,
    last_login_at           TIMESTAMPTZ
    );

-- ── Constraints ──────────────────────────────────────────────────────────────

ALTER TABLE users
    ADD CONSTRAINT chk_tier CHECK (tier IN ('FREE', 'PRO', 'ENTERPRISE'));

-- FREE users must have no expiry; paid users must have a start date
ALTER TABLE users
    ADD CONSTRAINT chk_tier_dates CHECK (
        (tier = 'FREE'  AND tier_expires_at IS NULL) OR
        (tier != 'FREE' AND tier_start_date IS NOT NULL)
        );

-- OAuth fields must both be present or both absent
ALTER TABLE users
    ADD CONSTRAINT chk_oauth CHECK (
        (oauth_provider IS NOT NULL AND oauth_provider_id IS NOT NULL) OR
        (oauth_provider IS NULL     AND oauth_provider_id IS NULL)
        );

-- ── Indexes ───────────────────────────────────────────────────────────────────

-- Primary login lookup: excludes deleted accounts (partial index)
CREATE UNIQUE INDEX idx_users_email
    ON users (email)
    WHERE deleted_at IS NULL;

-- Stripe webhook handler lookup
CREATE UNIQUE INDEX idx_users_stripe_customer
    ON users (stripe_customer_id)
    WHERE stripe_customer_id IS NOT NULL;

-- Background job scans for expired paid tiers nightly
CREATE INDEX idx_users_tier_expires
    ON users (tier_expires_at)
    WHERE tier != 'FREE';

-- OAuth login matching (composite for provider+ID pair)
CREATE INDEX idx_users_oauth
    ON users (oauth_provider, oauth_provider_id)
    WHERE oauth_provider IS NOT NULL;

-- ── Auto-update trigger for updated_at ───────────────────────────────────────

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE users IS 'Central identity table. Every other table FK references this.';
COMMENT ON COLUMN users.password_hash IS 'BCrypt cost-12 hash. NULL for OAuth-only users.';
COMMENT ON COLUMN users.tier_expires_at IS 'NULL for FREE (never expires). Set on upgrade.';
COMMENT ON COLUMN users.deleted_at IS 'Non-null = soft-deleted. Excluded from unique indexes.';