# Life Experience Discovery Portal

> This is my first vibecoding experience — the project is being built in collaboration with AI assistants.

Private web portal for a closed community. Catalog of activities and variants with per-user state (bookmarks, tried/want-to-try statuses). Invite-only, server-side rendered, Russian UI.

## Domain model

- `Activity` — *what* to try (e.g. SUP, pottery, cycling). Fixed list of 9 categories.
- `Variant` — *how* to try it differently, attached to an activity (e.g. "SUP at sunset").
- `Tag` — user-creatable, linked to activities and variants via two separate join tables.
- `UserExperience` — per-user status on an activity or variant: `INTERESTING` / `WANT_TO_TRY` / `TRIED` / `WANT_REPEAT`.
- `Bookmark` — saved item, mirrors the user-experience target shape.
- `Invite` — reusable code, 7-day TTL, traces inviter.

## Tech stack

- Java 21, Spring Boot 3.4.4
- PostgreSQL 18 with native ENUMs, `citext`, partial indexes
- Flyway migrations
- Spring Data JDBC (not JPA), Spring Security, Thymeleaf
- Spring Session JDBC for session storage
- Bucket4j for rate limiting
- Cloudflare R2 (S3-compatible) + Thumbnailator for media
- Testcontainers + JUnit 5 for integration tests

## Architecture

- Monolith, server-side rendering (Thymeleaf), no SPA.
- Persistence: Spring Data JDBC over Postgres. Aggregate roots, no lazy loading, explicit queries.
- ID generation: UUIDv7 via native Postgres 18 `uuidv7()`. App inserts with NULL id, DB generates.
- Auditing: timestamps managed by Spring JDBC Auditing (`@CreatedDate` / `@LastModifiedDate`); DB defaults kept as fallback.
- Soft delete via `deleted_at`, visibility filtered through `visible_*` views.
- Auth: form login, session-based, sessions persisted in DB via spring-session-jdbc.
- Auth bootstrap: first admin seeded manually; all other accounts require an invite.
- Media: uploads stored in R2, served via signed URLs; resize through Thumbnailator before upload.

## Access

Invite-only. No open registration. Codes are reusable, expire after 7 days.

## Run

```bash
docker compose up -d    # Postgres
./mvnw spring-boot:run
```

App starts at `http://localhost:8080`.

## Status

Active development. MVP — functionality and UI are evolving.

---

# Перевод на русский

> Это мой первый опыт vibecoding — проект собирается в коллаборации с AI-ассистентами.

Приватный веб-портал для закрытого сообщества. Каталог активностей и их вариантов с пользовательскими состояниями (закладки, статусы «попробовал / хочу попробовать»). Доступ по инвайтам, server-side rendering, русский UI.

## Доменная модель

- `Activity` — *что* попробовать (например, SUP, гончарное дело, велосипед). Фиксированный список из 9 категорий.
- `Variant` — *как* попробовать иначе, привязан к активности (например, «SUP на закате»).
- `Tag` — пользовательские теги, связанные с активностями и вариантами через две отдельные join-таблицы.
- `UserExperience` — статус пользователя по активности или варианту: `INTERESTING` / `WANT_TO_TRY` / `TRIED` / `WANT_REPEAT`.
- `Bookmark` — сохранённая позиция, повторяет форму UserExperience.
- `Invite` — переиспользуемый код, TTL 7 дней, фиксирует кто пригласил.

## Стек

- Java 21, Spring Boot 3.4.4
- PostgreSQL 18 с native ENUM, `citext`, partial-индексами
- Flyway-миграции
- Spring Data JDBC (не JPA), Spring Security, Thymeleaf
- Spring Session JDBC для хранения сессий
- Bucket4j для rate limiting
- Cloudflare R2 (S3-compatible) + Thumbnailator для медиа
- Testcontainers + JUnit 5 для интеграционных тестов

## Архитектура

- Монолит, server-side rendering (Thymeleaf), без SPA.
- Persistence: Spring Data JDBC поверх Postgres. Aggregate roots, без lazy loading, явные запросы.
- Генерация ID: UUIDv7 через нативный `uuidv7()` в Postgres 18. Приложение шлёт INSERT с NULL id, БД генерирует.
- Аудит timestamps: Spring JDBC Auditing (`@CreatedDate` / `@LastModifiedDate`); DB-defaults оставлены как страховка.
- Soft delete через `deleted_at`, видимость через view `visible_*`.
- Аутентификация: form login, сессии, хранятся в БД через spring-session-jdbc.
- Bootstrap: первый админ засеян вручную; все остальные регистрируются по инвайту.
- Медиа: загрузки в R2, отдача через signed URLs; ресайз через Thumbnailator до загрузки.

## Доступ

Invite-only. Открытой регистрации нет. Коды переиспользуемые, истекают через 7 дней.

## Запуск

```bash
docker compose up -d    # Postgres
./mvnw spring-boot:run
```

Приложение поднимается на `http://localhost:8080`.

## Статус

Активная разработка. MVP — функциональность и UI развиваются.
