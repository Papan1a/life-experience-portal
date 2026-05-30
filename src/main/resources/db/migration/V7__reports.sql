-- ============================================================
-- V7 — reports table: user complaints on activities/variants
-- ============================================================

CREATE TABLE reports (
    id                UUID PRIMARY KEY DEFAULT uuidv7(),
    reporter_user_id  UUID NOT NULL REFERENCES users(id),
    target_type       TEXT NOT NULL CHECK (target_type IN ('activity', 'variant')),
    target_id         UUID NOT NULL,
    reason            TEXT NOT NULL,
    comment           TEXT,
    status            TEXT NOT NULL DEFAULT 'NEW'
                      CHECK (status IN ('NEW', 'REVIEWED', 'RESOLVED', 'DISMISSED')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at       TIMESTAMPTZ,
    resolved_by       UUID REFERENCES users(id)
);

CREATE INDEX idx_reports_status    ON reports (status);
CREATE INDEX idx_reports_target    ON reports (target_type, target_id);
CREATE INDEX idx_reports_created   ON reports (created_at DESC);