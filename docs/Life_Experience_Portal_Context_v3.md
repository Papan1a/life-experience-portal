# Life_Experience_Portal_Context_v3.md

**Status:** Rewrite of v2, aligned with `MVP_SCOPE_v3_decisions.md`.
**Date:** 2026-05-22
**Supersedes:** `Life_Experience_Portal_Context_v2.md`, `MVP_SCOPE.md` (where they conflict).
**Doc language:** English (spec). **Product UI language:** Russian.

---

# Project Description

Private web portal for a small group of friends (dozens of users).

Purpose: help people discover activities, hobbies, experiences and unusual life opportunities they may never have considered.

Core idea: people often do not know what exists. The portal exposes possibilities.

Examples: SUP, SUP at sunset, pottery, cycling, astronomy, board games, language learning, photography, hiking, roller skating, unusual experiences.

The portal is NOT about: competition, achievements, rankings, engagement loops, gamification.

The portal IS about: inspiration, discovery, exploration, personal growth, sharing experiences, discovering unusual variations.

Framing:
- "Try SUP" → better: "Try SUP at sunset"
- "Ride bicycle" → better: "Ride to the neighboring town" / "Ride without navigation" / "Meet sunrise by bicycle"

Core philosophy:
- Activity = WHAT to try
- Variant = HOW to try it differently

---

# Users

Small private community. Authentication required. Registration is invite-only; no open registration.

Registration flow:
- A user generates an invitation code/link.
- Code is **reusable** until it expires; **no per-use limit**.
- TTL = 7 days from creation.
- New user must supply a valid invite to register.
- Without an invite, registration is impossible.

System stores inviter relationship: `invited_by_user_id`.

Bootstrap: the first admin user is seeded manually (has no inviter), resolving the invite-only chicken-and-egg.

Consent: at registration the user accepts a consent checkbox + short privacy note; `consent_at` is stored. (Minimal GDPR posture; export/deletion flow deferred.)

Goal: preserve friend-community nature; potential future invite-graph analytics (reserved).

---

# Visibility Model

MVP: everything is visible to all registered users.

Architectural reserve: visibility modes `PRIVATE` / `PUBLIC` ("only me" / "all users"). Not built in MVP, but the data model leaves room.

---

# Social Model

No friend requests. No approval process. Everyone sees everyone.

User discovery: user search and/or a people module.

Profile is visible to all registered users and contains:
- `display_name`
- `bio`
- `avatar`
- planned / saved / tried activities
- future statistics (reserved)

Purpose: inspiration, not competition.

---

# Roles & Permissions

MVP has **no separate Moderator role**. A single `is_admin: bool` flag on User, plus direct DB access for rare fixes.

User permissions:
- create Activity, create Variant (both become `ACTIVE` immediately)
- edit own Activity/Variant (while not archived/blocked)
- bookmark (save) activities/variants
- leave UserExperience
- generate invite
- send a report (see Reports)

Admin permissions (via `is_admin`):
- edit any content
- archive content
- change categories/tags on any content
- normalize/fix tags
- hide unsafe content
- receive report notifications

Reserved: full Moderator role + moderation tooling + merge UI.

---

# Main UI

Language: Russian.

After login:
- Search
- Catalog
- Saved
- Friends section

Catalog: categories visible (fixed RU seed); browse + search.

Search (MVP): category filter + tag search. No full-text search. (Title is used only for duplicate detection at creation.)

Saved: bookmarks list. Separate from experience statuses.

Experience views: the four statuses — Interesting / Want to try / Tried / Want repeat.

Friends area: compact, passive information only. Examples: "Alex wants pottery", "John tried SUP", "Maria planning hiking". No feed optimization, no rankings, no likes, no achievements, no notifications (passive section only).

---

# Entity Structure

```
Category
  ↓
Activity
  ↓
Variant (0..N)
```

Supporting systems (MVP): Tags, UserExperience, Bookmark, Lifecycle, Avatar/file storage (avatars only), is_admin.

Out of MVP (reserved): Reports entity, Collections, Visibility modes, Variant nesting, Merge.

---

# Category System

One mandatory category per Activity. No hierarchy.

Bad: Sport → Water → Board → SUP
Preferred: Category + Tags.

Category list is a **fixed seed**, not editable at runtime (changes go through migration).

Seed (9, Russian labels):
- Спорт (Sport)
- Обучение (Learning)
- Творчество (Creativity)
- Природа (Nature)
- Путешествия (Travel)
- Технологии (Technology)
- Общение (Social)
- Дом и быт (Home and Life)
- Необычный опыт (Unusual Experience)

---

# Tags

Flexible structure, no inheritance trees. Tags build semantic connections.

Example — Activity "SUP sunset", Category "Спорт", Tags: water, nature, balance, sunset, outside.

Rules:
- Suggest/autocomplete existing tags.
- Users may create new tags.
- Case-normalized + trimmed on write to suppress duplicates.
- Admin may normalize/fix tags (manual; no merge tooling in MVP).

Reserved: smart duplicate detection, smart tag suggestion.

---

# Activity

Activity = the core thing. Examples: SUP, Pottery, Planetarium, Cycling.

Fields:
- title
- description
- category (one, mandatory)
- tags
- complexity
- estimated_cost → **enum tier: `free` / `low` / `mid` / `high`** (no currency/amount)
- estimated_duration
- requirements
- interesting_reason
- created_by
- status
- deleted_at (nullable)
- archived_at (nullable)

Lifecycle: `DRAFT` → `ACTIVE` → `ARCHIVED`; plus `BLOCKED`. (`MERGED` removed — no merge in MVP.)

Soft delete only. No hard delete. An Activity may exist without a Variant (e.g. Planetarium).

---

# Variant

Variant enriches an Activity. It is NOT a separate Activity.

Example — Activity "SUP": Variants "SUP sunset", "SUP winter", "SUP picnic", "SUP early morning".

Fields:
- title
- description
- difference_reason
- extra_requirements
- tags
- created_by
- (`parent_variant_id` — **removed from MVP**; nesting reserved, cheap nullable FK to add later)

Created as `ACTIVE` immediately (no review step).

Lifecycle: `DRAFT` → `ACTIVE` → `ARCHIVED`. (`PENDING` and `MERGED` removed.)

Reserved: nested Variants (SUP → SUP sunset → SUP sunset winter).

---

# Duplicate Prevention (MVP)

On Activity creation, the system checks for a case-insensitive `title` match.

If matched: show a **non-blocking warning** + suggestion "create a Variant of X instead?". The user may proceed regardless.

No merge, no `redirect_to`, no canonical concepts/aliases in MVP. Duplicates are resolved manually in the DB by admin.

Reserved: merge mechanic (`status=MERGED`, `redirect_to=target`), canonical concepts, AI duplicate detection.

---

# Similar Activities (MVP)

Minimal: "others in the same category" (single category query).

Reserved: similarity by shared tags + characteristics (e.g. Pottery → Drawing, Wood carving, Calligraphy).

---

# UserExperience

Tracks a user's relationship to an Activity and/or Variant.

Grain: Activity and Variant are tracked **independently**. A user may simultaneously hold a status on the Activity (`variant_id` NULL) and on a specific Variant.

Uniqueness: `UNIQUE(user_id, activity_id, variant_id)`, where `variant_id IS NULL` = the activity-level row. (Postgres NULL-in-unique handled via partial unique index / COALESCE — decided at data-model session.)

Fields: user_id, activity_id, variant_id (nullable), status, note (nullable), created_at.

Statuses: `INTERESTING` / `WANT_TO_TRY` / `TRIED` / `WANT_REPEAT`.

MVP keeps **one mutable row** per (user, activity, variant); `status` changes in place. No history rows.

Reserved: status-change history (e.g. "Tried SUP Jun 2025 → again Aug 2025 → SUP sunset May 2026") — schema leaves room, no migration needed later.

---

# Bookmark (Save)

Separate, lightweight "keep for later" action, independent of UserExperience statuses.

Targets an Activity or a Variant (symmetric with UserExperience grain).

Fields: user_id, target (activity_id / variant_id), created_at.

The Home "Saved" list = bookmarks. Save and an experience status can coexist on the same target.

---

# Reports (MVP)

A "Report" button sends a **notification to the admin**. **No Report entity** is stored in the DB.

Users can flag: duplicate, unsafe content, spam, wrong category, bad description, other (conveyed in the notification).

Reserved: Report entity + lifecycle `NEW` / `IN_PROGRESS` / `RESOLVED`.

---

# Unsafe Content Rules

Forbidden: alcohol, drugs, illegal activities, dangerous challenges, unsafe experiences.

Goal: a safe inspiration catalog.

Enforcement in MVP: report button → admin notification → admin edits/archives/blocks via `is_admin`. No automated moderation.

---

# Media / Avatar Handling

General image upload is OUT of MVP. A **narrow avatar-upload subsystem is IN** (avatars only).

Requires: storage backend, format/size validation, resize to fixed dimensions.

Technical limits (backend local vs S3, allowed formats, max size, target dims) finalized at the stack session.

---

# Notifications

Passive only: the Home Friends section. No bell, no email, no push in MVP. (Consistent with anti-engagement philosophy.)

Reserved: active notification channels.

---

# Search (MVP)

Fields: category filter + tags. No title full-text search. (Title used only for duplicate detection.)

Reserved: full-text search, semantic search, duplicate detection.

---

# Long-term Reserved Architecture

Visibility modes (`PRIVATE`/`PUBLIC`), UserExperience history, Moderator role + tooling, Report entity & lifecycle, Merge + canonical concepts, AI duplicate detection, nested Variants, full-text/semantic search, smart tag suggestion, Collections, similarity engine, invite-graph analytics, active notifications, general image uploads, GDPR export/deletion, runtime-editable categories.

---

# Technical Principles

- Private, friend-oriented system. Remote hosting, international access.
- UI language: Russian.
- Simple UI. Future-proof, extensible data model.
- Soft delete everywhere.
- Consent stored at registration.
- Narrow avatar upload; otherwise no media uploads.
- Solo-developer-realistic scope. Avoid overengineering.

Philosophy: keep MVP implementation simple, keep the foundation strong.

---

# Open Questions (deferred, non-blocking)

- Avatar technical limits (storage backend, formats, max size, resize dims) → stack session.
- Field validation (max lengths, required vs optional per field) → data-model session.
