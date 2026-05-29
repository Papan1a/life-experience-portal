# Backlog — доработки и фиксы

Этот файл ведётся вручную. Сюда записываются идеи, запланированные доработки и известные проблемы.

---

## В работе

| Issue | Задача | Инструкция | Примечания |
|---|---|---|---|
| #21 | Снять отметку повторным кликом (убрать кнопку «Снять отметку») | [`tasks/issue-21-status-toggle.md`](tasks/issue-21-status-toggle.md) | Чистый Thymeleaf, без JS |

---

## Запланировано

Идентификатор — номер Issue из `ISSUES.md`. Файл-инструкция — в `docs/tasks/`.

| Issue | Задача | Инструкция | Приоритет / примечания |
|---|---|---|---|
| #1 | Удаление своих активностей и вариантов | [`tasks/issue-1-delete-content.md`](tasks/issue-1-delete-content.md) | soft-delete (`deleted_at` уже в схеме) |
| #9 | Выровнять кнопки статусов и «В избранное» по высоте | [`tasks/issue-9-button-alignment.md`](tasks/issue-9-button-alignment.md) | Высокий; ранее НЕ РЕШЕНО — подозреваемый `mt-4` |
| #12 | Управление профилем (имя/фото) | [`tasks/issue-12-profile-management.md`](tasks/issue-12-profile-management.md) | Почти готово; фото зависит от #7 / R2 |
| #24 | Ошибка «Запрос не выполнен (400)» при вводе тегов | [`tasks/issue-24-tag-suggest-400.md`](tasks/issue-24-tag-suggest-400.md) | См. «Известные баги» #5 |
| #26 | Раскладка категорий: минимизировать число строк | [`tasks/issue-26-category-layout.md`](tasks/issue-26-category-layout.md) | Косметика (CSS / порядок категорий) |
| #28 | Карточка активности кликабельна целиком | [`tasks/issue-28-clickable-card.md`](tasks/issue-28-clickable-card.md) | Stretched link (`::after`); зона звёздочки неактивна под #33 |
| #32 | Баг: на `/saved` отметка «В избранном» меняется не у той карточки | [`tasks/issue-32-bookmark-saved-toggle.md`](tasks/issue-32-bookmark-saved-toggle.md) | Дубль `id` → `<form>` корень + `hx-target="this"` |
| #34 | Баг: создание активности молча не срабатывает (рассинхрон `<select>`↔enum) | [`tasks/issue-34-create-activity-enum-mismatch.md`](tasks/issue-34-create-activity-enum-mismatch.md) | Опции из `values()` + видимые ошибки валидации |
| #35 | Автотесты на «тихие» баги форм (зависит от #34) | [`tasks/issue-35-form-validation-tests.md`](tasks/issue-35-form-validation-tests.md) | Инвариант: каждая опция select принимается сервером |
| #36 | Архдолг: переходы статусов в сервис + унификация slug/`parseTags`/404 | [`tasks/issue-36-status-transitions-service.md`](tasks/issue-36-status-transitions-service.md) | Из аудита; связано с #1 (delete в сервисе) |
| #37 | `@PreAuthorize` неактивна — включить `@EnableMethodSecurity` | [`tasks/issue-37-method-security.md`](tasks/issue-37-method-security.md) | Из аудита; проверить `ROLE_ADMIN` |
| #38 | Вынести логику аватара в `AvatarService` | [`tasks/issue-38-avatar-service.md`](tasks/issue-38-avatar-service.md) | Из аудита; чистый рефактор, связан с #12/#7 |
| #19 | Выбор аватара из предложенных + отображение (топ-бар/профиль) | [`tasks/issue-19-avatar-presets.md`](tasks/issue-19-avatar-presets.md) | Пресеты-статика (не зависит от R2); онлайн-виджет вне scope; нужны файлы в `static/images/avatars/` |
| #25, #29, #30, #31, #18 | Эпик: раскладка и единый каркас страниц | [`tasks/epic-layout-redesign.md`](tasks/epic-layout-redesign.md) | Делать одним заходом — правки в общем `main.css` конфликтуют |
| — | Юнит-тесты сервисов | [`tasks/unit-tests-services.md`](tasks/unit-tests-services.md) | Из раздела «Идеи» |
| — | R2 avatar URL — заглушка вместо реального URL | — | Высокий (prod), `UserProfileController.java`; связано с #7/#12-фото |

---

## Выполнено

| # | Задача | Файлы | Дата |
|---|---|---|---|
| | 1 | Улучшение flow регистрации (Вариант B) | `InviteController.java`, `invite/show.html`, `invite/list.html`, `register.html`, `login.html`, `UserController.java` | 26.05.2026 |
| | 2 | DevSeed — seed тестовых данных при профиле `dev` | `DevSeedRunner.java`, `BootstrapApplicationRunner.java`, `application.yml`, `docker-compose.yml`, `StartupSmokeTest.java` | 26.05.2026 |
| | 4 | Активация кнопок статуса/закладки на странице активности | `_layout.html`, `_status_buttons.html`, `_bookmark_button.html`, `_report_button.html`, `JdbcConvertersConfig.java`, `UserExperience.java`, `ExperienceService.java`, `ExperienceController.java`, `UserExperienceRepository.java`, `RepositoryIntegrationTest.java` | 26.05.2026 |
| | 5 | Тесты на рендеринг HTMX-URL в шаблонах | `HtmxTemplateRenderingTest.java` | 26.05.2026 |
| | 9 | Одноразовые инвайты + лимит 5 активных (Issues #1, #7) | `V6__invite_one_shot.sql`, `InviteStatus.java`, `Invite.java`, `InviteRepository.java`, `InviteService.java`, `UserService.java`, `InviteController.java`, `invite/list.html`, `invite/show.html`, `main.css`, `AuthIntegrationTest.java` | 27.05.2026 |
| | 11 | "Мой опыт": названия вместо UUID (Issue #8) | `ExperiencePageController.java` | 27.05.2026 |
| | 16 | Точки входа для создания активностей (Issue #2) | `ActivityController.java`, `index.html`, `activity.html`, `MyActivitiesController.java`, `my_activities.html`, `_layout.html`, `CatalogService.java`, `MyActivitiesIntegrationTest.java` | 27.05.2026 |
| | 10 | Логин по email регистронезависимый (Issue #3) | `UserService.java`, `PortalUserDetailsService.java` | 27.05.2026 |
| | 13 | Аватар-заглушка при отсутствии фото (Issue #10) | `default-avatar.svg` | 27.05.2026 |
| | 14 | Закрепить верхнее меню — sticky header (Issue #14) | `main.css`, `_layout.html` | 28.05.2026 |
| | 20 | Трёхколоночный макет + "Что делают другие" в правой колонке (Issue #20) | `_layout.html`, `main.css`, `catalog/index.html` | 28.05.2026 |
| | 14 | Профиль: убрать "Био не заполнено", показать email (Issues #11a, #11b) | `user/profile.html`, `profile.html` | 27.05.2026 |
| | 7 | Tech debt: обновления и deprecations (частично: 7.2, 7.4, 7.5) | `AdminController.java`, `UserController.java`, `AdminIntegrationTest.java`, `UserProfileIntegrationTest.java` | 27.05.2026 |
| | 8 | Косметика: чистка кода | `PortalUserDetails.java`, `CatalogIntegrationTest.java`, `UserController.java`, `JdbcConvertersConfig.java`, `WebConfig.java`, `additional-spring-configuration-metadata.json` | 27.05.2026 |
| | 4 | Обновить счётчики тестов в AI_IMPLEMENTATION_GUIDE.md (блок 2: 12 → 13) | `AI_IMPLEMENTATION_GUIDE.md` | 28.05.2026 |
| | 15 | Управление профилем: смена пароля и email (Issue #12) | `UserService.java`, `UserProfileController.java`, `edit.html`, `UserServiceTest.java` | 28.05.2026 |
| | 4 | Бесконечная подгрузка карточек вместо «Показать ещё» | `CatalogController.java`, `catalog/_cards.html`, `catalog/index.html`, `CatalogIntegrationTest.java`, `EndToEndTest.java`, `ExperienceIntegrationTest.java`, `StartupSmokeTest.java` | 29.05.2026 |
| | 16 | Редизайн страницы входа под стилистику портала | `main.css`, `login.html`, `register.html`, `main_background2.png` | 30.05.2026 |
---

## Известные баги

| # | Баг | Где | Приоритет |
|---|---|---|---|
| | 1 | ~~Задублированные поля в `register.html`~~ | `templates/register.html` | Исправлено в задаче #1 |
| | 2 | ~~T4.2 тест передаёт `"advanced"` вместо `"high"`~~ — в коде уже `"high"`, тест проходит | `ContentCreationIntegrationTest.java:184` | Исправлено 28.05.2026 |
| | 3 | ~~T4.6 тег-поиск — assertion failure~~ — тест проходит; `????` в логах = артефакт кодировки консоли Windows, на БД/ответ не влияет | `ContentCreationIntegrationTest.java` | Исправлено 28.05.2026 |
| | 4 | ~~docker-compose.yml теряет `stringtype=unspecified`~~ — исправлено добавлением параметра в JDBC URL. Симптом: enum-колонки PostgreSQL отказывают при INSERT через Spring Data JDBC. | `docker-compose.yml:30` | Исправлено 27.05.2026 |
| | 5 | **(Issue #24) Ошибка «⚠️ Запрос не выполнен (400)» при вводе тегов на `/activities/new`.** НЕ исправлено. См. разбор гипотез ниже. | `catalog/activity_form.html`, `TagController.java`, `_layout.html:165` | Открыт |

### Баг #5 — разбор причин (ошибка 400 при автодополнении тегов)

**Что известно:**
- Сообщение «Запрос не выполнен (400)» — это ветка `else` обработчика `htmx:responseError` в `_layout.html:153-170`. Значит сервер реально отвечает **HTTP 400** (а не 401/403/5xx).
- Глобального `@ControllerAdvice`/`@ExceptionHandler` в проекте нет → 400 это стандартный ответ Spring MVC.
- Endpoint `TagController.suggest` объявляет `@RequestParam("q")` — параметр **обязателен**.
- **Уже отвергнуто:** первая попытка чинить через смену имени параметра (фронт слал `tags`, заменено на `hx-vals="js:{q: ...}"`) — ошибка **повторяется**. Значит дело не просто в имени параметра, либо `q` всё равно не доходит до сервера.

**Гипотезы (по убыванию вероятности):**

1. **`q` всё равно не доставляется до сервера → `MissingServletRequestParameterException`.** Выражение `hx-vals="js:{...}"` может не выполняться (переменная `event`/`event.target` недоступна или даёт `undefined` при данном триггере в этой версии HTMX), и тогда параметр `q` не добавляется к запросу. Это даёт ровно 400. **Проверка:** DevTools → Network → кликнуть на запрос к `/tags/suggest` и посмотреть, есть ли в URL `?q=...` и что в теле ответа 400 (Spring пишет имя недостающего параметра).

2. **Параметр объявлен обязательным.** Даже если основной ввод работает, любой сценарий без `q` (очистили поле; js-выражение вернуло пусто/undefined) → 400. Кандидат на устранение: сделать `@RequestParam(value="q", required=false, defaultValue="")` — сервис `getTagsByPrefix` уже корректно обрабатывает пустой ввод (возвращает пустой список).

3. **Рассинхрон `datalist`/`ul` (вторично, само по себе 400 не даёт, но механизм сломан).** Фрагмент `catalog/_tag_suggestions.html` возвращает `<ul id="tag-suggestions">`, а форма ждёт `<datalist id="tag-suggestions">` (атрибут `list` у поля). После первого ответа HTMX заменяет `datalist` на `ul`, и нативное автодополнение перестаёт работать. Починить заодно при общем решении.

4. **`StrictHttpFirewall` (низкая вероятность).** Дефолтный фильтр Spring Security возвращает 400 на запросы с некоторыми символами (`;`, `%`, управляющие). При обычном вводе тегов маловероятно, но проверить, если 400 появляется только при определённых символах.

5. **Кодировка кириллицы в query (низкая).** Редко даёт именно 400, но исключить при диагностике.

**Следующий шаг диагностики:** запустить приложение и поймать реальный запрос в Network — URL (есть ли `q`) + тело ответа 400. Это однозначно разведёт гипотезы 1–2 от 4–5.

---

## Идеи (не запланировано)

- Замена `main.css` на Pico CSS или Tabler для более профессионального вида (после завершения функционала)
- Добавить тесты на недостающие сценарии из аудита блоков 1–4 (13 новых тестов)
- HikariPool warnings в тестах — настроить `maxLifetime` в `application.yml` для тестового профиля
- Pre-commit hook: grep `hx-(post|get|put|delete|patch)\s*=\s*"@\{` в `src/main/resources/templates/` для блокировки коммита с необработанными Thymeleaf-выражениями в HTMX-атрибутах (быстрее запуска тестов)