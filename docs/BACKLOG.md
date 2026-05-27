# Backlog — доработки и фиксы

Этот файл ведётся вручную. Сюда записываются идеи, запланированные доработки и известные проблемы.

---

## В работе

*Нет активных задач.*

---

## Запланировано

| # | Задача | Приоритет | Примечания |
|---|---|---|---|
| | 9 | Одноразовые инвайты + лимит 5 активных (Issues #1, #7) | Высокий | [Подробный план](tasks/issue-01-invite-one-shot.md) |
| | 10 | Логин по email регистронезависимый (Issue #3) | Средний | [План](tasks/issue-03-case-insensitive-login.md) |
| | 11 | "Мой опыт": показывать названия вместо UUID (Issue #8) | Высокий | [План](tasks/issue-08-my-experiences-titles.md) |
| | 12 | Выровнять кнопки на странице активности (Issue #9) | Низкий | [План](tasks/issue-09-buttons-alignment.md) |
| | 13 | Аватар-заглушка при отсутствии фото (Issue #10) | Низкий | [План](tasks/issue-10-default-avatar.md) |
| | 14 | Профиль: убрать "Био не заполнено", показать email (Issues #11a, #11b) | Низкий | [План](tasks/issue-11-profile-bio-email.md) |
| | 15 | Управление профилем: смена пароля и email (Issue #12) | Средний | [План](tasks/issue-12-profile-edit-controls.md) |
| | 3 | Автологаут через 5 минут бездействия | Средний | `SessionConfig.java` (300 сек), `_layout.html` (JS-таймер) |
| | 4 | Обновить счётчики тестов в AI_IMPLEMENTATION_GUIDE.md (блоки 1–4) | Низкий | T1.9–11, T2.9–11, T3.9–11, T4.9–12 |
| | 6 | R2 avatar URL — заглушка `/r2/...` вместо реального URL | Высокий (prod) | `UserProfileController.java:149` |
| | 7 | Tech debt: обновления и deprecations | Низкий | См. деталку ниже |
| | 8 | Косметика: чистка кода | Очень низкий | См. деталку ниже |

---

### Деталка задачи #7 — Tech debt: обновления и deprecations

Цель: убрать предупреждения о deprecated API и устаревших версиях. Не блокирует работу, но накапливается тех-долг.

| Подзадача | Файлы | Действие |
|---|---|---|
| 7.1 | `pom.xml` | Обновить Spring Boot 3.4.4 → 3.5.14 (OSS support 3.4.x закончился 2025-12-31). Прогнать все тесты после апгрейда |
| 7.2 | `AdminIntegrationTest.java:58`, `UserProfileIntegrationTest.java` | Заменить `@MockBean` (deprecated с 3.4.0) на `@MockitoBean` из `org.springframework.test.context.bean.override.mockito` |
| 7.3 | Все 8 тестов с `PostgreSQLContainer` (`AdminIntegrationTest`, `AuthIntegrationTest`, `CatalogIntegrationTest`, `ContentCreationIntegrationTest`, `EndToEndTest`, `ExperienceIntegrationTest`, `RepositoryIntegrationTest`, `UserProfileIntegrationTest`) | `PostgreSQLContainer<?>` deprecated в Testcontainers 2.0+. Перейти на `@ServiceConnection` (Spring Boot 3.1+) — убирает необходимость `@DynamicPropertySource` |
| 7.4 | `AdminController.java:29` | Удалить неиспользуемое поле `private static final Logger log` |
| 7.5 | `UserController.java:28` | Удалить неиспользуемое поле `authenticationManager` (осталось от старого автологина, теперь используется ручная установка `SecurityContextHolder`) |

**Проверка после реализации:**
- `mvn test` — все 77+ тестов зелёные
- `docker compose up --build -d` — приложение стартует без ошибок
- VS Code Problems — категория Java/Boot warnings уменьшилась как минимум на ~15 пунктов

---

### Деталка задачи #8 — Косметика: чистка кода

Цель: убрать мелкие косметические замечания IDE. Чистый код, ничего не ломается.

| Подзадача | Файлы | Действие |
|---|---|---|
| 8.1 | `PortalUserDetails.java:12` | Убрать `implements Serializable` — `UserDetails` уже extends `Serializable` |
| 8.2 | `CatalogIntegrationTest.java:57` | Удалить неиспользуемое поле `@Autowired InviteRepository inviteRepository` |
| 8.3 | `UserController.java:85-95` | Объединить два `catch` с одинаковым телом (`InvalidInviteException` и `IllegalArgumentException`) через multicatch `\|` |
| 8.4 | `application.yml` | Создать `src/main/resources/META-INF/additional-spring-configuration-metadata.json` со схемой кастомных properties (`r2.*`, `app.*`, `admin-bootstrap.*`) — уберёт `YAML_UNKNOWN_PROPERTY` warnings |
| 8.5 | `application.yml:50-52, 70-71, 93` | Экранировать ключи с email-точками (например `admin@local.dev` → `[admin@local.dev]`) — уберёт `YAML_SHOULD_ESCAPE` |
| 8.6 | `JdbcConvertersConfig.java:30`, `WebConfig.java:11` | Добавить `@NonNull` аннотации к параметрам, унаследованным от Spring-интерфейсов |

**Что НЕ делать в этой задаче (false positives):**
- Не трогать "Null type safety" warnings про UUID — это шум от строгого null-анализа, реальной проблемы нет
- Не трогать "configure/setUp/tearDown is never used" — это методы JUnit, вызываются через рефлексию
- Не трогать "Resource leak: Closeable never closed" про `PostgreSQLContainer` — Testcontainers сам управляет lifecycle

**Опциональная альтернатива:** отключить строгий null-анализ в `.vscode/settings.json`:
```json
{ "java.compile.nullAnalysis.mode": "disabled" }
```
Это уберёт ~70+ предупреждений одним движением, но скроет и потенциально полезные.

---

## Выполнено

| # | Задача | Файлы | Дата |
|---|---|---|---|
| | 1 | Улучшение flow регистрации (Вариант B) | `InviteController.java`, `invite/show.html`, `invite/list.html`, `register.html`, `login.html`, `UserController.java` | 26.05.2026 |
| | 2 | DevSeed — seed тестовых данных при профиле `dev` | `DevSeedRunner.java`, `BootstrapApplicationRunner.java`, `application.yml`, `docker-compose.yml`, `StartupSmokeTest.java` | 26.05.2026 |
| | 4 | Активация кнопок статуса/закладки на странице активности | `_layout.html`, `_status_buttons.html`, `_bookmark_button.html`, `_report_button.html`, `JdbcConvertersConfig.java`, `UserExperience.java`, `ExperienceService.java`, `ExperienceController.java`, `UserExperienceRepository.java`, `RepositoryIntegrationTest.java` | 26.05.2026 |
| | 5 | Тесты на рендеринг HTMX-URL в шаблонах | `HtmxTemplateRenderingTest.java` | 26.05.2026 |

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
