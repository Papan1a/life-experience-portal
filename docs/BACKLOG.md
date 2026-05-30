# Backlog — доработки и фиксы

Этот файл ведётся вручную. Сюда записываются идеи, запланированные доработки и известные проблемы.

---

## В работе

| Issue | Задача | Инструкция | Примечания |
|---|---|---|---|
| — | (нет активных задач) | — | — |

---

## Запланировано

Идентификатор — номер Issue из `ISSUES.md`. Файл-инструкция — в `docs/tasks/`.

| Issue | Задача | Инструкция | Приоритет / примечания |
|---|---|---|---|
| #9 | Выровнять кнопки статусов и «В избранное» по высоте | [`tasks/issue-9-button-alignment.md`](tasks/issue-9-button-alignment.md) | Истинная причина — padded-блок жалобы; решение = вынести жалобу (в #13) + center |
| #13 | Система жалоб: хранение в БД + админка + UI-модалка | [`tasks/issue-13-reports-system.md`](tasks/issue-13-reports-system.md) | Объединяет исходный #13 (reports не сохраняются) + новый UI (`<dialog>`, лимит 200, resize:none); связан с #9 |
| #12 | Управление профилем (имя/фото) | [`tasks/issue-12-profile-management.md`](tasks/issue-12-profile-management.md) | Почти готово; фото зависит от #7 / R2 |
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
| #21 | Снятие отметки повторным кликом (убрана кнопка «Снять отметку») | `experience/_status_buttons.html` | 30.05.2026 |
| #1 | Удаление активностей и вариантов (soft-delete) | `CatalogService.java`, `ActivityController.java`, `VariantController.java`, `activity.html`, `variant.html`, `my_activities.html`, `MyActivitiesIntegrationTest.java` | 30.05.2026 |
| #24 | Ошибка 400 при вводе тегов | `variant_form.html`, `activity_form.html`, `TagController.java`, `_tag_suggestions.html` | 30.05.2026 |
| | 16 | Редизайн страницы входа под стилистику портала | `main.css`, `login.html`, `register.html`, `main_background2.png` | 30.05.2026 |
| #26 | Раскладка категорий: чипсы укладываются в ≤2 строки (компактный CSS чипсов) | `main.css` | 30.05.2026 |
| #28 | Карточка активности кликабельна целиком (stretched link через `::after`) | `main.css` | 30.05.2026 |
| #32 | Баг `/saved`: отметка «В избранном» меняется не у той карточки (фрагмент → `<form>` + `hx-target="this"`, убран дубль `id`) | `experience/_bookmark_button.html` | 30.05.2026 |
---

## Известные баги

| # | Баг | Где | Приоритет |
|---|---|---|---|
| | 1 | ~~Задублированные поля в `register.html`~~ | `templates/register.html` | Исправлено в задаче #1 |
| | 2 | ~~T4.2 тест передаёт `"advanced"` вместо `"high"`~~ — в коде уже `"high"`, тест проходит | `ContentCreationIntegrationTest.java:184` | Исправлено 28.05.2026 |
| | 3 | ~~T4.6 тег-поиск — assertion failure~~ — тест проходит; `????` в логах = артефакт кодировки консоли Windows, на БД/ответ не влияет | `ContentCreationIntegrationTest.java` | Исправлено 28.05.2026 |
| | 4 | ~~docker-compose.yml теряет `stringtype=unspecified`~~ — исправлено добавлением параметра в JDBC URL. Симптом: enum-колонки PostgreSQL отказывают при INSERT через Spring Data JDBC. | `docker-compose.yml:30` | Исправлено 27.05.2026 |
| | 5 | ~~**(Issue #24) Ошибка «⚠️ Запрос не выполнен (400)» при вводе тегов.**~~ Исправлено 30.05.2026. Причина: `variant_form.html` слал `tags` вместо `q` через `hx-include`. | — | Исправлено |
---

## Идеи (не запланировано)

- Замена `main.css` на Pico CSS или Tabler для более профессионального вида (после завершения функционала)
- Добавить тесты на недостающие сценарии из аудита блоков 1–4 (13 новых тестов)
- HikariPool warnings в тестах — настроить `maxLifetime` в `application.yml` для тестового профиля
- Pre-commit hook: grep `hx-(post|get|put|delete|patch)\s*=\s*"@\{` в `src/main/resources/templates/` для блокировки коммита с необработанными Thymeleaf-выражениями в HTMX-атрибутах (быстрее запуска тестов)