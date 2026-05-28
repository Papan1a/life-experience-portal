# Backlog — доработки и фиксы

Этот файл ведётся вручную. Сюда записываются идеи, запланированные доработки и известные проблемы.

---

## В работе

*Нет активных задач.*

---

## Запланировано

| # | Задача | Приоритет | Примечания |
|---|---|---|---|
| | 15 | Управление профилем: смена пароля и email (Issue #12) | Средний | [План](tasks/issue-12-profile-edit-controls.md) |
| | 3 | Автологаут через 5 минут бездействия | Средний | `SessionConfig.java` (300 сек), `_layout.html` (JS-таймер) |
| | 4 | Обновить счётчики тестов в AI_IMPLEMENTATION_GUIDE.md (блоки 1–4) | Низкий | T1.9–11, T2.9–11, T3.9–11, T4.9–12 |
| | 6 | R2 avatar URL — заглушка `/r2/...` вместо реального URL | Высокий (prod) | `UserProfileController.java:149` |
| | 12 | Выровнять кнопки на странице активности (Issue #9), не выполнено, для случая когда кнопки "интересно", "Хочу попробовать", "Попробовал(а)", "Хочу повторить" - не нажаты. В этом случае контролы "Сохранить" и "Сообщить о проблеме" рисуются на некоторое количество пикселей выше общего уровня других контролов. Та же проблема сохраняется на странице просмотра Вариантов активности. Возможно это из-за выравнивания разных контролов по нижнему краю| Низкий | предыдущие правки решили проблему неполностью, `activity.html`, `_report_button.html` |
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
| | 12 | Выровнять кнопки на странице активности (Issue #9) | `activity.html`, `_report_button.html` | 27.05.2026 |
| | 13 | Аватар-заглушка при отсутствии фото (Issue #10) | `default-avatar.svg` | 27.05.2026 |
| | 14 | Закрепить верхнее меню — sticky header (Issue #14) | `main.css`, `_layout.html` | 28.05.2026 |
| | 20 | Трёхколоночный макет + "Что делают другие" в правой колонке (Issue #20) | `_layout.html`, `main.css`, `catalog/index.html` | 28.05.2026 |
| | 14 | Профиль: убрать "Био не заполнено", показать email (Issues #11a, #11b) | `user/profile.html`, `profile.html` | 27.05.2026 |
| | 7 | Tech debt: обновления и deprecations (частично: 7.2, 7.4, 7.5) | `AdminController.java`, `UserController.java`, `AdminIntegrationTest.java`, `UserProfileIntegrationTest.java` | 27.05.2026 |
| | 8 | Косметика: чистка кода | `PortalUserDetails.java`, `CatalogIntegrationTest.java`, `UserController.java`, `JdbcConvertersConfig.java`, `WebConfig.java`, `additional-spring-configuration-metadata.json` | 27.05.2026 |

---

## Известные баги

| # | Баг | Где | Приоритет |
|---|---|---|---|
| | 1 | ~~Задублированные поля в `register.html`~~ | `templates/register.html` | Исправлено в задаче #1 |
| | 2 | T4.2 тест передаёт `"advanced"` вместо `"high"` | `ContentCreationIntegrationTest.java:185` | Средний |
| | 3 | T4.6 тег-поиск — assertion failure (требует диагностики body) | `ContentCreationIntegrationTest.java:299` | Средний |
| | 4 | docker-compose.yml теряет `stringtype=unspecified` — исправлено добавлением параметра в JDBC URL. Симптом: enum-колонки PostgreSQL отказывают при INSERT через Spring Data JDBC. | `docker-compose.yml:30` | Исправлено 27.05.2026 |

---

## Идеи (не запланировано)

- Замена `main.css` на Pico CSS или Tabler для более профессионального вида (после завершения функционала)
- Добавить тесты на недостающие сценарии из аудита блоков 1–4 (13 новых тестов)
- HikariPool warnings в тестах — настроить `maxLifetime` в `application.yml` для тестового профиля
- Pre-commit hook: grep `hx-(post|get|put|delete|patch)\s*=\s*"@\{` в `src/main/resources/templates/` для блокировки коммита с необработанными Thymeleaf-выражениями в HTMX-атрибутах (быстрее запуска тестов)
