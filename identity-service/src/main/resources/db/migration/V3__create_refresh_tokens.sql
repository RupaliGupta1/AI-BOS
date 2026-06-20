-- ============================================================
-- V3__create_refresh_tokens.sql
-- AI BOS — Refresh token rotation table
-- ============================================================

CREATE TABLE IF NOT EXISTS refresh_tokens (
                                              id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(255) NOT NULL,
    device_hint  VARCHAR(200),
    ip_address   VARCHAR(45),
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked      BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
    );

-- ── Constraints ───────────────────────────────────────────────────────────────

-- If revoked, revoked_at must be set
ALTER TABLE refresh_tokens
    ADD CONSTRAINT chk_rt_revoked CHECK (
        (revoked = FALSE AND revoked_at IS NULL) OR
        (revoked = TRUE  AND revoked_at IS NOT NULL)
        );

-- ── Indexes ───────────────────────────────────────────────────────────────────

-- Primary lookup on every token use (hash lookup = constant time)
CREATE UNIQUE INDEX idx_rt_token_hash
    ON refresh_tokens (token_hash);

-- List active sessions for security dashboard
-- Partial index: only non-revoked tokens (revoked are dead records)
CREATE INDEX idx_rt_user_id
    ON refresh_tokens (user_id)
    WHERE revoked = FALSE;

-- Background cleanup job: finds expired tokens
-- Partial index: skip already-revoked tokens
CREATE INDEX idx_rt_expires_at
    ON refresh_tokens (expires_at)
    WHERE revoked = FALSE;

COMMENT ON TABLE refresh_tokens IS
    'Server-side refresh token state. Plain tokens are NEVER stored — only SHA-256 hash.';
COMMENT ON COLUMN refresh_tokens.token_hash IS
    'SHA-256(plain_token). Plain token returned to client once and never persisted.';
COMMENT ON COLUMN refresh_tokens.revoked IS
    'TRUE after use (rotation) or explicit logout. Revoked tokens kept 30 days for audit.';