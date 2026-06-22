-- ============================================================
-- V4__create_projects.sql
-- AI BOS — Project Management
-- ============================================================

CREATE TABLE IF NOT EXISTS projects (
                                        id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project_name           VARCHAR(100) NOT NULL,
    idea_description       VARCHAR(2000) NOT NULL,
    industry               VARCHAR(100),
    status                 VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    analysis_version       INTEGER      NOT NULL DEFAULT 1,
    cloned_from_project_id UUID         REFERENCES projects(id) ON DELETE SET NULL,
    failure_reason         TEXT,
    version                BIGINT       NOT NULL DEFAULT 0,
    archived_at            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
    );

-- ── Constraints ───────────────────────────────────────────────────────────────

ALTER TABLE projects
    ADD CONSTRAINT chk_project_status CHECK (
        status IN ('CREATED','QUEUED','RUNNING','COMPLETED','FAILED','PARTIAL')
        );

ALTER TABLE projects
    ADD CONSTRAINT chk_project_name_length CHECK (char_length(project_name) <= 100);

ALTER TABLE projects
    ADD CONSTRAINT chk_idea_description_length CHECK (char_length(idea_description) <= 2000);

-- Prevent a project from being marked as cloned from itself
ALTER TABLE projects
    ADD CONSTRAINT chk_no_self_clone CHECK (cloned_from_project_id IS DISTINCT FROM id);

-- ── Indexes ───────────────────────────────────────────────────────────────────

-- Hot path #1: "list user's projects, reverse chronological, exclude archived"
CREATE INDEX idx_projects_user_created
    ON projects (user_id, created_at DESC)
    WHERE archived_at IS NULL;

-- Hot path #2: "count active projects for quota check" / per-user status filtering
CREATE INDEX idx_projects_user_status
    ON projects (user_id, status)
    WHERE archived_at IS NULL;

-- Future hot path: RabbitMQ workers polling "all QUEUED projects across all users"
-- Deliberately NOT filtered by archived_at — a worker query is system-wide, not per-user.
CREATE INDEX idx_projects_status
    ON projects (status);

-- ── Auto-update trigger for updated_at (reuses function from V1) ──────────────

CREATE TRIGGER trg_projects_updated_at
    BEFORE UPDATE ON projects
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ── Comments ──────────────────────────────────────────────────────────────────

COMMENT ON TABLE projects IS
    'User-owned business analysis projects. Soft-deleted via archived_at.';
COMMENT ON COLUMN projects.version IS
    'Optimistic locking token. Prevents lost updates when RabbitMQ workers and user actions race on status changes.';
COMMENT ON COLUMN projects.cloned_from_project_id IS
    'Self-referencing FK set when this project was created via the Clone action. NULL for originals.';
COMMENT ON COLUMN projects.failure_reason IS
    'Populated when status transitions to FAILED or PARTIAL. NULL otherwise.';
COMMENT ON COLUMN projects.analysis_version IS
    'Schema/prompt version used for the AI analysis pipeline. Allows re-running older projects against newer agent logic.';