-- ============================================================
-- Life Experience Discovery Portal — MVP schema (PostgreSQL)
-- Aligned with: MVP_SCOPE_v3_decisions.md, Life_Experience_Portal_Context_v3.md
-- Data-model decisions: D0 Postgres, D1 UUIDv7, D2 status+deleted_at,
--   D3 native ENUM, D4 NULLS NOT DISTINCT, D5 mirror target + composite FK,
--   D6 two tag join tables, D7 query-time visibility, D8 invite traceability.
-- ============================================================

-- ---- UUID generation ------------------------------------------
--   ids are generated in the DB via uuidv7() (built-in PG 18+).
--   App (Spring Data JDBC) inserts with NULL id -> treated as new ->
--   DB generates the key and returns it (avoids the isNew() ambiguity).

CREATE EXTENSION IF NOT EXISTS citext;

-- ============================================================
-- ENUM types (D3)
-- ============================================================
CREATE TYPE activity_status   AS ENUM ('DRAFT','ACTIVE','ARCHIVED','BLOCKED');
CREATE TYPE variant_status    AS ENUM ('DRAFT','ACTIVE','ARCHIVED');
CREATE TYPE experience_status AS ENUM ('INTERESTING','WANT_TO_TRY','TRIED','WANT_REPEAT');
CREATE TYPE cost_tier         AS ENUM ('free','low','mid','high');
CREATE TYPE complexity_level  AS ENUM ('low','medium','high');

-- ============================================================
-- updated_at trigger helper
-- ============================================================
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- users
--   Auth: email + password (argon2id). Admin-assisted reset — no email infra in MVP.
--   Email verification skipped in MVP (invite-only trust).
-- ============================================================
CREATE TABLE users (
    id                 uuid PRIMARY KEY DEFAULT uuidv7(),
    email              citext NOT NULL UNIQUE,
    password_hash      text NOT NULL,
    display_name       text NOT NULL,
    bio                text,
    avatar_url         text,
    is_admin           boolean NOT NULL DEFAULT false,
    invited_by_user_id uuid REFERENCES users(id) ON DELETE RESTRICT,
    invite_id          uuid,
    consent_at         timestamptz NOT NULL DEFAULT now(),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    deleted_at         timestamptz
);
CREATE TRIGGER trg_users_updated BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- invites — reusable code, no per-use limit, TTL = created + 7d
-- ============================================================
CREATE TABLE invites (
    id          uuid PRIMARY KEY DEFAULT uuidv7(),
    code        text NOT NULL UNIQUE,
    created_by  uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_invites_code ON invites(code);

ALTER TABLE users
    ADD CONSTRAINT fk_users_invite
    FOREIGN KEY (invite_id) REFERENCES invites(id) ON DELETE SET NULL;

-- ============================================================
-- categories — fixed lookup, RU labels, runtime-immutable
-- ============================================================
CREATE TABLE categories (
    id         uuid PRIMARY KEY DEFAULT uuidv7(),
    slug       text NOT NULL UNIQUE,
    label_ru   text NOT NULL,
    sort_order smallint NOT NULL DEFAULT 0,
    is_active  boolean NOT NULL DEFAULT true
);

INSERT INTO categories (slug, label_ru, sort_order) VALUES
    ('sport',      'Спорт',          10),
    ('learning',   'Обучение',       20),
    ('creativity', 'Творчество',     30),
    ('nature',     'Природа',        40),
    ('travel',     'Путешествия',    50),
    ('technology', 'Технологии',     60),
    ('social',     'Общение',        70),
    ('home_life',  'Дом и быт',      80),
    ('unusual',    'Необычный опыт', 90);

-- ============================================================
-- activities
-- ============================================================
CREATE TABLE activities (
    id                 uuid PRIMARY KEY DEFAULT uuidv7(),
    title              text NOT NULL,
    description        text,
    category_id        uuid NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    complexity         complexity_level,
    cost_tier          cost_tier,
    estimated_duration text,
    requirements       text,
    interesting_reason text,
    created_by         uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status             activity_status NOT NULL DEFAULT 'ACTIVE',
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    deleted_at         timestamptz
);

CREATE INDEX idx_activities_lower_title ON activities (lower(title));
CREATE INDEX idx_activities_category    ON activities (category_id);
CREATE INDEX idx_activities_visible     ON activities (status) WHERE deleted_at IS NULL;
CREATE TRIGGER trg_activities_updated BEFORE UPDATE ON activities
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- variants — created ACTIVE; UNIQUE(id, activity_id) enables composite FKs (D5)
-- ============================================================
CREATE TABLE variants (
    id                 uuid PRIMARY KEY DEFAULT uuidv7(),
    activity_id        uuid NOT NULL REFERENCES activities(id) ON DELETE RESTRICT,
    title              text NOT NULL,
    description        text,
    difference_reason  text,
    extra_requirements text,
    created_by         uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status             variant_status NOT NULL DEFAULT 'ACTIVE',
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    deleted_at         timestamptz,
    UNIQUE (id, activity_id)
);
CREATE INDEX idx_variants_activity ON variants (activity_id);
CREATE TRIGGER trg_variants_updated BEFORE UPDATE ON variants
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- tags — user-creatable, case-normalized (slug)
-- ============================================================
CREATE TABLE tags (
    id         uuid PRIMARY KEY DEFAULT uuidv7(),
    slug       text NOT NULL UNIQUE,
    name       text NOT NULL,
    created_by uuid REFERENCES users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- ============================================================
-- activity_tags / variant_tags (D6)
-- ============================================================
CREATE TABLE activity_tags (
    activity_id uuid NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    tag_id      uuid NOT NULL REFERENCES tags(id)       ON DELETE CASCADE,
    PRIMARY KEY (activity_id, tag_id)
);
CREATE INDEX idx_activity_tags_tag ON activity_tags (tag_id);

CREATE TABLE variant_tags (
    variant_id uuid NOT NULL REFERENCES variants(id) ON DELETE CASCADE,
    tag_id     uuid NOT NULL REFERENCES tags(id)     ON DELETE CASCADE,
    PRIMARY KEY (variant_id, tag_id)
);
CREATE INDEX idx_variant_tags_tag ON variant_tags (tag_id);

-- ============================================================
-- user_experiences (D4 uniqueness, D5 target shape)
-- ============================================================
CREATE TABLE user_experiences (
    id          uuid PRIMARY KEY DEFAULT uuidv7(),
    user_id     uuid NOT NULL REFERENCES users(id)      ON DELETE CASCADE,
    activity_id uuid NOT NULL REFERENCES activities(id) ON DELETE RESTRICT,
    variant_id  uuid,
    status      experience_status NOT NULL,
    note        text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (variant_id, activity_id)
        REFERENCES variants(id, activity_id) ON DELETE RESTRICT,
    CONSTRAINT uq_user_experience
        UNIQUE NULLS NOT DISTINCT (user_id, activity_id, variant_id)
);
CREATE INDEX idx_ux_user     ON user_experiences (user_id);
CREATE INDEX idx_ux_activity ON user_experiences (activity_id);
CREATE TRIGGER trg_ux_updated BEFORE UPDATE ON user_experiences
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- bookmarks (D5) — mirrors user_experiences target shape
-- ============================================================
CREATE TABLE bookmarks (
    id          uuid PRIMARY KEY DEFAULT uuidv7(),
    user_id     uuid NOT NULL REFERENCES users(id)      ON DELETE CASCADE,
    activity_id uuid NOT NULL REFERENCES activities(id) ON DELETE RESTRICT,
    variant_id  uuid,
    created_at  timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (variant_id, activity_id)
        REFERENCES variants(id, activity_id) ON DELETE RESTRICT,
    CONSTRAINT uq_bookmark
        UNIQUE NULLS NOT DISTINCT (user_id, activity_id, variant_id)
);
CREATE INDEX idx_bookmarks_user ON bookmarks (user_id);

-- ============================================================
-- Visibility views (D7) — query-time filter, no cascade writes
-- ============================================================
CREATE VIEW visible_activities AS
    SELECT * FROM activities
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

CREATE VIEW visible_variants AS
    SELECT v.*
    FROM variants v
    JOIN activities a ON a.id = v.activity_id
    WHERE v.status = 'ACTIVE' AND v.deleted_at IS NULL
      AND a.status = 'ACTIVE' AND a.deleted_at IS NULL;
