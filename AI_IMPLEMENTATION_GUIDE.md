# AI Implementation Guide — Life Experience Discovery Portal

**Для:** AI-модели, реализующей проект пошагово через VS Code API.
**Контекст:** Прочитай все файлы в этой директории перед началом. Канонические документы:
- `Life_Experience_Portal_Context_v3.md` — продуктовые решения и MVP-граница
- `data_model_v1.md` — решения D0–D8, ERD, таблицы
- `schema.sql` — исполняемый DDL (источник правды)
- `architecture_v1.md` — стек S1–S9, структура проекта, security
- `docker-compose.yml` + `db.Dockerfile` — инфраструктура

**Стек:** Spring Boot + Thymeleaf + HTMX, PostgreSQL, Spring Data JDBC, Flyway, Cloudflare R2, Docker Compose + Caddy.

---

## Общие правила работы

### Retry-логика (применяется везде)
Если тест или проверка падает:
1. Прочитай вывод ошибки полностью.
2. Исправь причину — не симптом.
3. Запусти тест снова.
4. Повтори до **3 раз**.
5. Если после 3 попыток тест всё ещё падает → **СТОП**.

При СТОП:
- Выведи точный текст ошибки.
- Опиши что пробовал и почему не помогло.
- Укажи файл и строку, где предположительно проблема.
- Жди указаний человека.

### Правила генерации кода
- Не генерируй код за пределами текущего блока.
- Не изменяй `schema.sql` — это источник правды. Flyway-миграции копируют его.
- Используй UUIDv7 (DB-side, `uuidv7()` — встроенная функция PostgreSQL 18+). Не генерируй UUID в приложении.
- Soft delete везде: никогда не делай `DELETE` для контентных таблиц.
- `created_at` / `updated_at` управляются через Spring Data JDBC Auditing (`@CreatedDate` / `@LastModifiedDate`). Не устанавливай их вручную в Java-коде.
- Язык UI — русский. Язык кода, комментариев, логов — английский.

### Порядок коммитов
После каждого блока, когда все тесты прошли:
```
git add .
git commit -m "block-N: <краткое описание>"
```

---

## БЛОК 0 — Scaffold проекта

**Цель:** рабочая структура Maven-проекта, Docker Compose поднимается, приложение стартует.

### Шаги

**0.1 — pom.xml**
Создай `pom.xml` со следующими зависимостями:
- `spring-boot-starter-web`
- `spring-boot-starter-thymeleaf`
- `spring-boot-starter-data-jdbc`
- `spring-boot-starter-security`
- `spring-session-jdbc`
- `flyway-core`
- `flyway-database-postgresql`
- `postgresql` (driver)
- `aws-sdk-java-v2` (модуль `s3`)
- `thumbnailator` (аватары)
- `spring-boot-starter-test`
- `testcontainers` (модули `junit-jupiter`, `postgresql`)

Java 21, Spring Boot 3.4.x.

**0.2 — Структура директорий**
Создай пустые пакеты по `architecture_v1.md` §3:
```
src/main/java/com/lep/portal/
  PortalApplication.java
  config/
  common/
  user/
  invite/
  catalog/
  experience/
  admin/
src/main/resources/
  application.yml
  db/migration/
  templates/
  static/
src/test/java/com/lep/portal/
```

**0.3 — application.yml**
```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/lep}
    username: ${SPRING_DATASOURCE_USERNAME:lep}
    password: ${SPRING_DATASOURCE_PASSWORD:lep}
  flyway:
    enabled: true
    locations: classpath:db/migration
  session:
    store-type: jdbc
    jdbc:
      initialize-schema: never   # Spring Session DDL создаётся V2

r2:
  endpoint: ${R2_ENDPOINT:}
  bucket: ${R2_BUCKET:}
  access-key-id: ${R2_ACCESS_KEY_ID:}
  secret-access-key: ${R2_SECRET_ACCESS_KEY:}

app:
  avatar-max-bytes: ${APP_AVATAR_MAX_BYTES:2097152}
```

**0.4 — Flyway-миграции**
- `V1__init.sql` = полная копия `schema.sql`
- `V2__spring_session.sql` = Spring Session JDBC DDL для PostgreSQL (возьми из `spring-session-jdbc` jar: `org/springframework/session/jdbc/schema-postgresql.sql`)

**0.5 — Docker Compose**
Используй готовый `docker-compose.yml`. Добавь `app.Dockerfile` (multi-stage):
```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
```

Добавь `Caddyfile` с проксированием на `app:8080`.

**0.6 — PortalApplication.java**
```java
@SpringBootApplication
public class PortalApplication {
    public static void main(String[] args) {
        SpringApplication.run(PortalApplication.class, args);
    }
}
```

### Проверки блока 0

```bash
# T0.1 — сборка
./mvnw package -DskipTests
# Ожидание: BUILD SUCCESS

# T0.2 — Docker Compose (db + adminer)
docker compose up -d db adminer
docker compose ps
# Ожидание: db healthy

# T0.3 — Flyway миграции применяются
docker compose up -d app
docker compose logs app | grep "Successfully applied"
# Ожидание: "Successfully applied 2 migrations"

# T0.4 — приложение стартует
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/
# Ожидание: 302 (redirect to /login) или 200
```

**Если T0.1 падает** → проверь зависимости в pom.xml, версии, репозитории Maven.
**Если T0.3 падает** → проверь что V1__init.sql идентичен schema.sql; проверь что `db.Dockerfile` использует `postgres:18` (`uuidv7()` встроенная).
**Если после 3 попыток** → СТОП.

---

## БЛОК 1 — Доменные агрегаты и репозитории

**Важно (БЛОК 1.5):** В проект добавлены:
- `JdbcAuditingConfig` с `@EnableJdbcAuditing` — управляет `@CreatedDate` / `@LastModifiedDate`
- `V3__remove_timestamp_triggers.sql` — дропает все триггеры `set_updated_at()`
- Java 21, PostgreSQL 18, `uuidv7()` встроенная
- Не устанавливай `createdAt` / `updatedAt` в конструкторах агрегатов — это делает Auditing


**Цель:** все Java-агрегаты (records/классы) + Spring Data JDBC репозитории + Testcontainers тесты репозиториев.

### Порядок создания

Создавай в строго таком порядке (от независимых к зависимым):

1. `common/` — `BaseAggregate`, хелперы `AggregateReference`, общие исключения
2. `catalog/Category` — агрегат + репозиторий
3. `catalog/Tag` — агрегат + репозиторий (нормализация slug)
4. `catalog/Activity` — агрегат + репозиторий + `ActivityTag` (owned collection)
5. `catalog/Variant` — агрегат + репозиторий + `VariantTag`
6. `user/User` — агрегат + репозиторий (без auth, только CRUD)
7. `invite/Invite` — агрегат + репозиторий
8. `experience/UserExperience` — агрегат + репозиторий
9. `experience/Bookmark` — агрегат + репозиторий

### Требования к агрегатам
- `@Id UUID id` — не генерировать в Java, вставлять `null` → БД генерирует через `uuidv7()` (встроенная функция PostgreSQL 18+).
- Поля enum как Java enum, маппинг через `JdbcConverter` или `@Column` с конвертером.
- Soft delete: репозитории содержат методы типа `findByIdAndDeletedAtIsNull(...)`.
- Видимость через вьюхи `visible_activities` / `visible_variants` — делай отдельные read-методы или используй `@Query` с этими вьюхами.

### Проверки блока 1

Создай `src/test/java/com/lep/portal/RepositoryIntegrationTest.java` на Testcontainers:

```
T1.1 — Category: найти все 9 категорий после миграции
T1.2 — Activity: создать → найти по id → проверить status=ACTIVE
T1.3 — Variant: создать с activity_id → найти → проверить FK
T1.4 — Tag: создать с дублирующим slug → ожидать UniqueConstraintException
T1.5 — UserExperience: создать activity-level (variant_id=null) и variant-level → оба существуют
T1.6 — UserExperience: попытка создать дубль → ожидать ConstraintViolationException
T1.7 — Bookmark: аналогично T1.5–T1.6
T1.8 — Soft delete: удалить activity (deleted_at=now) → visible_activities не возвращает её
T1.9 — Soft delete variant: установить deleted_at у variant → visible_variants не возвращает его, но findById находит
T1.10 — Bookmark variant-level: создать закладку с variantId → найти по findByUserActivityVariant
T1.11 — Tag.searchByName: поиск по части имени, case-insensitive
```

```bash
./mvnw test -Dtest=RepositoryIntegrationTest
# Ожидание: Tests run: 11, Failures: 0, Errors: 0
```

**Если T1.4 или T1.6 падают** → проверь constraints в schema.sql и маппинг enum-типов PG.
**Если T1.8 падает** → проверь @Query на visible_activities вьюхе.
**После 3 попыток** → СТОП.

---

## БЛОК 2 — Auth: регистрация и вход

**Цель:** рабочий flow регистрации по инвайту, вход, выход. Spring Security + Spring Session JDBC.

### Шаги

**2.1 — SecurityConfig**
- Form login на `/login`, logout на `/logout`
- Все маршруты кроме `/login`, `/register`, `/static/**` требуют аутентификации
- `Argon2PasswordEncoder` как `PasswordEncoder` bean
- CSRF включён; HTMX-запросы должны передавать CSRF-заголовок
- `UserDetailsService` читает `users` по email

**2.2 — Invite validation**
Сервис `InviteService`:
- `validateAndUse(code)` → проверяет: инвайт существует, `expires_at > now()`, `revoked_at IS NULL`
- Не помечает инвайт как использованный (он reusable до истечения TTL)
- Бросает `InvalidInviteException` если невалиден

**2.3 — Registration flow**
`GET /register?code={code}` → форма (поля: code скрытый, display_name, email, password, чекбокс consent)
`POST /register` → валидация:
  - Инвайт валиден (через InviteService)
  - email уникален
  - display_name не пустой
  - password минимум 8 символов
  - consent = true
  → создать User (`invited_by_user_id`, `invite_id`, `consent_at=now()`)
  → автологин → redirect `/`

**2.4 — Login/Logout**
`GET /login` → стандартная форма Thymeleaf
`POST /login` → Spring Security обрабатывает
`POST /logout` → инвалидация сессии

**2.5 — Invite generation**
`POST /invites/generate` (только авторизованный пользователь) → создать Invite (`code=random UUID`, `expires_at=now+7d`, `created_by=currentUser`) → redirect на `/invites/{id}` со страницей с кодом и ссылкой

### Шаблоны (Thymeleaf, RU)
- `templates/auth/login.html`
- `templates/auth/register.html`
- `templates/invite/show.html` — показывает сгенерированную ссылку

### Проверки блока 2

```
T2.1 — GET /login без сессии → 200
T2.2 — POST /login с неверным паролем → redirect /login?error
T2.3 — GET /register без кода → 400 или redirect
T2.4 — POST /register с просроченным/несуществующим инвайтом → ошибка на форме
T2.5 — POST /register с валидным инвайтом → пользователь создан в БД, redirect /
T2.6 — POST /register с тем же email повторно → ошибка на форме
T2.7 — GET / без сессии → redirect /login
T2.4b — POST /register с просроченным инвайтом → ошибка на форме
T2.8 — POST /invites/generate (авторизован) → инвайт создан, TTL=7 дней
T2.9 — POST /register без consent (consent=false) → 302 redirect, пользователь не создан в БД
T2.10 — POST /register с паролем <8 символов → 302 redirect, пользователь не создан в БД
T2.11 — POST /logout → сессия инвалидирована, последующий GET / → 302 redirect /login
```

```bash
./mvnw test -Dtest=AuthIntegrationTest
# Ожидание: Tests run: 12, Failures: 0, Errors: 0
```

**Если T2.4 падает** → проверь логику InviteService и передачу ошибки в модель.
**Если T2.5 падает** → проверь транзакцию: user + invite_id должны сохраниться атомарно.
**После 3 попыток** → СТОП.

---

## БЛОК 3 — Каталог: просмотр и поиск

**Цель:** главная страница с каталогом, фильтрация по категории и тегам, страница активити, страница варианта.

### Pre-flight: HTMX

Каталог использует HTMX (S1) для фильтрации без перезагрузки. Перед началом блока:
- Скачай `htmx.min.js` (последний стабильный, ~14KB) в `src/main/resources/static/js/htmx.min.js`
- Создай `templates/_layout.html` с `<script src="/js/htmx.min.js"></script>` и blocks для контента
- Перенеси существующие `login.html` / `register.html` на этот layout, если нужна консистентность header'ов

### Шаги

**3.1 — CatalogController**
`GET /` → главная:
  - Список категорий из БД (9 штук)
  - Список видимых активностей (из `visible_activities`)
  - Параметры запроса: `?category={slug}` и/или `?tags={tag1},{tag2}`
  - Пагинация: 20 активностей на страницу (`?page=N`)

**3.2 — ActivityController**
`GET /activities/{id}` → страница активити:
  - Поля активити
  - Список вариантов (из `visible_variants` где `activity_id={id}`)
  - Похожие: другие активити в той же категории (до 5, исключая текущую)
  - Статус текущего пользователя (из `user_experiences` если есть)
  - Закладка текущего пользователя (из `bookmarks` если есть)

**3.3 — VariantController**
`GET /activities/{activityId}/variants/{variantId}` → страница варианта:
  - Поля варианта
  - Ссылка на родительскую активити
  - Статус и закладка пользователя по этому варианту

**3.4 — Шаблоны (RU)**
- `templates/catalog/index.html` — главная, фильтры, список карточек
- `templates/catalog/activity.html` — страница активити
- `templates/catalog/variant.html` — страница варианта

Используй HTMX для фильтрации без перезагрузки страницы (`hx-get`, `hx-target`).

### Проверки блока 3

```
T3.1 — GET / (авторизован) → 200, содержит список активностей и категории
T3.2 — GET /?category=sport → только активности категории "Спорт"
T3.3 — GET /?tags=water → активности с тегом water
T3.4 — GET /activities/{id} → 200, варианты видны
T3.5 — GET /activities/{nonexistent} → 404
T3.6 — GET /activities/{archivedId} → 404 (невидима через вьюху)
T3.7 — GET /activities/{activityId}/variants/{variantId} → 200
T3.8 — HTMX фильтр: GET /?category=sport с заголовком HX-Request:true → возвращает фрагмент, не полную страницу
T3.9 — GET /activities/{activityId}/variants/{variantId} где variant принадлежит другой activity → 404
T3.10 — GET /activities/{id} для BLOCKED активити → 404
T3.11 — Пагинация: GET /?page=2 → отображается "Страница <strong>2</strong>"
```

```bash
./mvnw test -Dtest=CatalogIntegrationTest
# Ожидание: Tests run: 11, Failures: 0, Errors: 0
```

**Если T3.6 падает** → проверь что контроллер использует `visible_activities` вьюху, а не прямой запрос к `activities`.
**После 3 попыток** → СТОП.

---

## БЛОК 4 — Создание контента (Activity и Variant)

**Цель:** авторизованный пользователь может создавать активити и варианты, система предупреждает о дублях.

### Шаги

**4.1 — ActivityCreateController**
`GET /activities/new` → форма создания:
  - Поля: title, description, category (select), complexity (select), cost_tier (select), estimated_duration, requirements, interesting_reason
  - Поле tags: autocomplete существующих тегов (HTMX endpoint `/tags/suggest?q={q}`)

`POST /activities` → обработка:
  1. Валидация полей (title обязателен, category обязательна)
  2. Dup-check: `lower(title)` → поиск в `activities`. Если найдено:
     - Вернуть форму с **нон-блокирующим** предупреждением: "Похожая активность уже существует: {title}. Создать вариант вместо этого?"
     - Кнопки: "Всё равно создать" (hidden input `force=true`) и "Создать вариант" (redirect на форму варианта)
  3. Нормализовать теги: `lower(trim(name))` → найти или создать Tag
  4. Создать Activity со `status=ACTIVE`, `created_by=currentUser`
  5. Redirect на `/activities/{id}`

**4.2 — VariantCreateController**
`GET /activities/{activityId}/variants/new` → форма:
  - Поля: title, description, difference_reason, extra_requirements, tags
  - Показывает родительскую активити

`POST /activities/{activityId}/variants` → обработка:
  1. Проверить что активити существует и ACTIVE
  2. Нормализовать теги
  3. Создать Variant со `status=ACTIVE`, `created_by=currentUser`
  4. Redirect на `/activities/{activityId}/variants/{id}`

**4.3 — Tag autocomplete**
`GET /tags/suggest?q={q}` → HTMX endpoint:
  - Поиск по `slug LIKE lower(trim(q))%`
  - Вернуть HTML-фрагмент с подсказками (до 10 тегов)

**4.4 — Edit собственного контента**
`GET /activities/{id}/edit` → форма редактирования (только автор или is_admin)
`POST /activities/{id}` → обновить (только автор или is_admin; status не меняется)
Аналогично для Variant.

### Шаблоны (RU)
- `templates/catalog/activity_form.html`
- `templates/catalog/variant_form.html`
- `templates/catalog/_dup_warning.html` — фрагмент предупреждения о дубле

### Проверки блока 4

```
T4.1 — GET /activities/new (авторизован) → 200
T4.2 — POST /activities с валидными данными → Activity создана, redirect
T4.3 — POST /activities без title → ошибка валидации на форме
T4.4 — POST /activities с title существующей активити → форма с предупреждением о дубле
T4.5 — POST /activities с title дубля + force=true → Activity создана несмотря на предупреждение
T4.6 — GET /tags/suggest?q=wa (HTMX) → список тегов начинающихся с "wa"
T4.7 — POST /activities/{activityId}/variants → Variant создан, status=ACTIVE
T4.8 — GET /activities/{id}/edit чужой активити (не автор, не admin) → 403
T4.9 — GET /activities/{id}/edit своей активити (автор) → 200, содержит title
T4.10 — POST /activities/{id} (update) с новым title → 302 redirect, title обновлён в БД
T4.11 — POST /activities без авторизации (без cookie) → 302 redirect /login
T4.12 — POST /activities/{id}/variants где activity.status=ARCHIVED → 404
```

```bash
./mvnw test -Dtest=ContentCreationIntegrationTest
# Ожидание: Tests run: 12, Failures: 0, Errors: 0
```

**Если T4.4 падает** → проверь что dup-check использует `lower(title)` индекс и что предупреждение передаётся в модель.
**Если T4.8 падает** → проверь security check в сервисе (сравнение `created_by == currentUserId || isAdmin`).
**После 3 попыток** → СТОП.

---

## БЛОК 5 — UserExperience и Bookmarks

**Цель:** пользователь может отмечать статус и сохранять активити/варианты.

### Шаги

**5.1 — ExperienceController**
`POST /experiences` (HTMX) → тело: `activity_id`, опционально `variant_id`, `status`
  - Если строка существует → обновить `status`
  - Если не существует → создать
  - Если `status=null` → удалить строку (снять отметку)
  - Вернуть HTML-фрагмент обновлённой кнопки статуса

**5.2 — BookmarkController**
`POST /bookmarks/toggle` (HTMX) → тело: `activity_id`, опционально `variant_id`
  - Если закладка есть → удалить; если нет → создать
  - Вернуть HTML-фрагмент кнопки закладки (toggled)

**5.3 — "Сохранённое" (Saved) раздел**
`GET /saved` → список закладок текущего пользователя:
  - Группировка: активити без варианта / с вариантами
  - Показывает статус из `user_experiences` рядом

**5.4 — "Мой опыт" раздел**
`GET /my-experiences` → все `user_experiences` текущего пользователя, сгруппированные по статусу:
  - Интересно / Хочу попробовать / Пробовал / Хочу повторить

### Шаблоны (RU)
- `templates/experience/saved.html`
- `templates/experience/my_experiences.html`
- `templates/experience/_status_buttons.html` — HTMX-фрагмент кнопок
- `templates/experience/_bookmark_button.html` — HTMX-фрагмент закладки

### Проверки блока 5

```
T5.1 — POST /experiences (WANT_TO_TRY) → строка создана, фрагмент возвращён
T5.2 — POST /experiences повторно с другим статусом → строка обновлена (не создана новая)
T5.3 — POST /experiences с variant_id → отдельная строка от activity-level
T5.4 — POST /experiences status=null → строка удалена
T5.5 — POST /bookmarks/toggle → закладка создана; повторно → удалена
T5.6 — GET /saved → 200, список закладок текущего пользователя
T5.7 — GET /my-experiences → 200, сгруппировано по статусу
T5.8 — GET /saved другого пользователя → недоступно (только свои закладки)
```

```bash
./mvnw test -Dtest=ExperienceIntegrationTest
# Ожидание: Tests run: 8, Failures: 0, Errors: 0
```

**Если T5.2 падает** → проверь логику upsert: `UNIQUE NULLS NOT DISTINCT` + update в сервисе.
**Если T5.3 падает** → проверь что composite FK `(variant_id, activity_id)` корректно передаётся.
**После 3 попыток** → СТОП.

---

## БЛОК 6 — Профили пользователей и Friends-раздел

**Цель:** профили видны всем авторизованным, Friends-раздел показывает активность друзей пассивно.

### Шаги

**6.1 — UserProfileController**
`GET /users/{id}` → профиль пользователя:
  - display_name, bio, avatar
  - Списки: хочет попробовать / пробовал / планирует (из `user_experiences`)
  - Только видимый контент (через `visible_activities` / `visible_variants`)

**6.2 — User search**
`GET /users?q={query}` → поиск по `display_name` (ILIKE)
  - HTMX endpoint для live-поиска

**6.3 — Friends-раздел на главной**
На `GET /` добавь секцию "Что делают другие":
  - Выборка: последние N записей `user_experiences` других пользователей (не текущего)
  - Формат: "{display_name} хочет попробовать {activity.title}" / "пробовал" / "планирует"
  - Максимум 20 записей, сортировка по `created_at DESC`
  - Никаких лайков, никаких уведомлений, только текст

**6.4 — Avatar upload**
`POST /users/me/avatar` (multipart/form-data):
  1. Валидация: content-type = image/jpeg или image/png; размер ≤ APP_AVATAR_MAX_BYTES
  2. Ресайз до 256×256 через Thumbnailator
  3. Загрузить в R2: ключ = `avatars/{userId}`
  4. Обновить `users.avatar_url`
  5. Redirect на профиль

**6.5 — Настройки профиля**
`GET /users/me/edit` → форма (display_name, bio, аватар)
`POST /users/me` → обновить display_name и bio

### Шаблоны (RU)
- `templates/user/profile.html`
- `templates/user/edit.html`
- `templates/user/search_results.html` — HTMX-фрагмент

### Проверки блока 6

```
T6.1 — GET /users/{id} (авторизован) → 200, содержит displayName и bio
T6.2 — GET /users/{id} (не авторизован) → redirect /login
T6.3 — GET /users/search?q=Профиль (HTMX) → список с совпадением, без несовпадений
T6.4 — GET / содержит Friends-секцию "Что делают другие" с чужими записями
T6.5 — Friends-секция не содержит записей текущего пользователя
T6.6 — POST /profile/avatar с валидным JPEG (MockBean S3Client) → avatar_url обновлён
T6.7 — POST /profile/avatar с файлом > 2 MB → ошибка, avatar_url не обновлён
T6.8 — POST /profile → display_name и bio обновлены в БД
```

```bash
./mvnw test -Dtest=UserProfileIntegrationTest
# Ожидание: Tests run: 8, Failures: 0, Errors: 0
```

**Особенности реализации:**
- S3Client в UserProfileController — `@Autowired(required = false)`, с проверкой на null
- В тестах используется `@MockBean S3Client` для изоляции от R2
- INSERT в activities требует `category_id` (NOT NULL) — используется подзапрос `(SELECT id FROM categories WHERE slug='sport')`
- `@MockBean` deprecation warning безопасен (Spring Boot 3.4.x), в будущем заменить на `@MockitoBean`
**После 3 попыток** → СТОП.

---

## БЛОК 7 — Admin-функции ✅

**Цель:** пользователь с `is_admin=true` может модерировать контент.

### Реализованные компоненты

**7.1 — AdminController** (`src/main/java/com/lep/portal/admin/AdminController.java`)
- `@PreAuthorize("hasRole('ADMIN')")` на уровне класса
- Инжектирует `ActivityRepository`, `VariantRepository`, `TagRepository`
- `GET /admin` → `admin/dashboard.html` с тремя таблицами: активности, варианты, теги
- `POST /admin/activities/{id}/archive` → `status=ARCHIVED`
- `POST /admin/activities/{id}/block` → `status=BLOCKED`
- `POST /admin/activities/{id}/activate` → `status=ACTIVE` (из ARCHIVED/BLOCKED)
- `POST /admin/variants/{id}/archive` → `status=ARCHIVED`
- `POST /admin/tags/{id}/rename` → обновляет `name` и `slug` (нормализация: lowercase + замена спецсимволов на `_` + trim)
- `GET /admin/reports` → заглушка с `reportsMessage` (функция отчётов в следующей версии)

**7.2 — ReportController** (`src/main/java/com/lep/portal/admin/ReportController.java`)
- `@RestController` (не `@Controller`)
- `POST /reports` — принимает `target_type` (activity/variant), `target_id`, `reason` (валидация против Set: duplicate/unsafe/spam/wrong_category/bad_description/other), опционально `comment`
- Логирует через `log.warn("REPORT: user=... target_type=... target_id=... reason=... comment=...")`
- Возвращает `ResponseEntity<String>` — текст ответа, не HTML (для HTMX)

**7.3 — Шаблоны:**
- `templates/admin/dashboard.html` — 3 таблицы с формами archive/block/activate (кнопки с `disabled` для недопустимых переходов), inline rename для тегов, flash-сообщения
- `templates/catalog/_report_button.html` — HTMX-фрагмент с `<details>` раскрывающимся блоком, select причины, опциональный comment, `hx-post="/reports"`, `hx-target="#report-result"`
- `templates/catalog/activity.html` — добавлен `<div th:replace="~{catalog/_report_button :: reportButton('activity', ${activity.id})}">` после статус/закладок
- `templates/catalog/variant.html` — добавлен `<div th:replace="~{catalog/_report_button :: reportButton('variant', ${variant.id})}">` после статус/закладок
- `templates/_layout.html` — добавлен `xmlns:sec="http://www.thymeleaf.org/extras/spring-security"` и `<a sec:authorize="hasRole('ADMIN')" href="/admin">Админ</a>`

**7.4 — Тесты:** `AdminIntegrationTest.java` (8 тестов)

### Проверки блока 7

```
T7.1 — GET /admin (is_admin=true) → 200, содержит "Админ-панель"
T7.2 — GET /admin (обычный пользователь) → 403
T7.3 — POST /admin/activities/{id}/archive → status=ARCHIVED, не видна в visible_activities
T7.4 — POST /admin/activities/{id}/activate → status=ACTIVE, видна в visible_activities
T7.5 — POST /admin/activities/{id}/block → status=BLOCKED, не видна в visible_activities
T7.6 — POST /reports с валидными данными → 200 "Спасибо"; невалидный reason → 400
T7.7 — POST /admin/tags/{id}/rename → slug нормализован (проверка в БД: name="Новое ИМЯ!!!", slug="новое_имя")
T7.8 — Дочерние варианты архивированной активити не видны в visible_variants (status варианта остаётся ACTIVE)
```

```bash
./mvnw test -Dtest=AdminIntegrationTest
# Результат: Tests run: 8, Failures: 0, Errors: 0 ✅
```

**Особенности реализации:**
- `R2Config` использует `@ConditionalOnExpression` — при отсутствии `r2.endpoint` бин `S3Client` не создаётся. В тестах `@MockBean S3Client` для изоляции.
- Admin-пользователь создаётся через `userRepository.save()` с `setAdmin(true)`, затем логин через `formLogin()`.
- `visible_variants` вьюха (из V1__init.sql) JOIN-ит `variants` с `activities` и проверяет статус обеих — T7.8 валидирует это поведение.
- `normalizeSlug()`: lowercase → `replaceAll("[^a-zа-яё0-9]+", "_")` → trim leading/trailing `_`.
- `@MockBean` deprecation warning безопасен (Spring Boot 3.4.x).

---

## БЛОК 8 — Bootstrap и финальные проверки

**Цель:** система полностью запускается на чистой машине, первый admin-пользователь создаётся через seed.

### Шаги

**8.1 — Seed первого admin**
Добавь Flyway-миграцию `V5__seed_admin.sql` (V3 уже используется для удаления триггеров, V4 — partial unique email):
```sql
-- Placeholder: пароль устанавливается вручную через Adminer после деплоя.
-- Или: читай из env при старте через DataSourceInitializer.
```
Альтернатива (рекомендуется): при старте приложения проверяй `SELECT COUNT(*) FROM users WHERE is_admin=true`. Если 0 → логируй инструкцию: "No admin user found. Create one via Adminer or set ADMIN_BOOTSTRAP_EMAIL/PASSWORD env vars." Если env vars заданы → создать пользователя автоматически.

**8.2 — Caddyfile**
```
{
    email your@email.com
}

yourdomain.com {
    reverse_proxy app:8080
}

yourdomain.com/adminer* {
    # IP restriction
    @allowed remote_ip 1.2.3.4
    handle @allowed {
        reverse_proxy adminer:8080
    }
    handle {
        respond 403
    }
}
```

**8.3 — End-to-end smoke test**
Полный сценарий на Testcontainers (поднять весь стек):
```
E2E.1 — Создать инвайт-код → зарегистрироваться по нему → войти
E2E.2 — Создать Activity → убедиться что видна в каталоге
E2E.3 — Создать Variant к Activity
E2E.4 — Поставить статус WANT_TO_TRY на Activity
E2E.5 — Добавить в закладки
E2E.6 — Второй пользователь видит первого в Friends-разделе
E2E.7 — Admin архивирует Activity → она исчезает из каталога
```

```bash
./mvnw test -Dtest=EndToEndTest
# Ожидание: Tests run: 7, Failures: 0, Errors: 0
```

**8.4 — Docker Compose full stack**
```bash
docker compose up -d
sleep 15
curl -s -o /dev/null -w "%{http_code}" http://localhost/login
# Ожидание: 200
docker compose logs app | grep -i error
# Ожидание: нет ERROR строк связанных со стартом
```

**После 3 попыток любого из E2E тестов** → СТОП.

---

## Итоговый чеклист перед сдачей

Перед финальным коммитом проверь каждый пункт:

- [ ] `./mvnw test` — все тесты зелёные
- [ ] `docker compose up -d` — все контейнеры healthy
- [ ] Нет `System.out.println` в production-коде (только `log.*`)
- [ ] Нет хардкода секретов (пароли, ключи R2) — всё через env vars
- [ ] `schema.sql` не изменён (изменения только через Flyway V2+)
- [ ] Все формы содержат CSRF-токен
- [ ] Все маршруты `/admin/**` возвращают 403 для не-admin пользователей
- [ ] Нет `DELETE FROM` в production-коде для контентных таблиц
- [ ] `updated_at` не устанавливается в Java-коде

---

## Зарезервировано (НЕ реализовывать в этой итерации)

Следующие функции намеренно исключены из MVP. Не добавляй их:

- Report entity в БД (только лог)
- Collections (кураторские подборки)
- Merge дубликатов (status=MERGED, redirect_to)
- Nested Variants (parent_variant_id)
- Видимость PRIVATE/PUBLIC
- История UserExperience (несколько строк на цель)
- Full-text / семантический поиск
- Уведомления (email, push, bell)
- Загрузка изображений кроме аватаров
- Moderator role (только is_admin)
- AI duplicate detection
- Login rate-limiting (счётчик неудачных попыток входа на IP, 429 после превышения). Bucket4j присутствует в `pom.xml`, реализация отложена до появления реальной нагрузки/атак.
