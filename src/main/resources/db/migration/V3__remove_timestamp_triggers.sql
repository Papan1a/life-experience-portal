-- ============================================================
-- V3 — Drop updated_at triggers and set_updated_at() function.
-- Timestamps are now managed by the application via Spring Data
-- JDBC Auditing (@CreatedDate / @LastModifiedDate).
-- Column DEFAULTs are kept as a DB-level fallback.
-- ============================================================

DROP TRIGGER IF EXISTS trg_users_updated      ON users;
DROP TRIGGER IF EXISTS trg_activities_updated ON activities;
DROP TRIGGER IF EXISTS trg_variants_updated   ON variants;
DROP TRIGGER IF EXISTS trg_ux_updated         ON user_experiences;

DROP FUNCTION IF EXISTS set_updated_at();
