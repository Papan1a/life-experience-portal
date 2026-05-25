-- ============================================================
-- V4: Partial unique index on users.email — allows re-registration
--     of an email address after the previous account is soft-deleted.
--
-- Closes architecture_v1.md §9 Open Item #3.
--
-- Before: UNIQUE (email) — global, blocks reuse even for deleted users.
-- After:  UNIQUE (email) WHERE deleted_at IS NULL — only active accounts.
--
-- UserRepository.emailExists and findByEmail already filter by
-- deleted_at IS NULL, so application and DB semantics now align.
-- ============================================================

ALTER TABLE users DROP CONSTRAINT users_email_key;

CREATE UNIQUE INDEX idx_users_email_active
    ON users (email)
    WHERE deleted_at IS NULL;
