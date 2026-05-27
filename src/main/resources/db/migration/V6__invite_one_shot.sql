-- One-shot invites: track who used the invite and when.
-- Status is computed from columns:
--   USED      → used_at IS NOT NULL
--   REVOKED   → revoked_at IS NOT NULL (and not used)
--   EXPIRED   → expires_at < now() (and not used/revoked)
--   ACTIVE    → otherwise

ALTER TABLE invites
    ADD COLUMN used_at         timestamptz,
    ADD COLUMN used_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL;

-- Speeds up "count active invites by creator" check for the 5-invite limit
CREATE INDEX idx_invites_active_by_creator
    ON invites(created_by)
    WHERE used_at IS NULL
      AND revoked_at IS NULL;
