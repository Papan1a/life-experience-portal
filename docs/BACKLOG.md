# Backlog — доработки и фиксы

Этот файл ведётся вручную. Сюда записываются идеи, запланированные доработки и известные проблемы.

---

## В работе

| # | Задача | Файлы | Статус |
|---|---|---|---|
| 1 | Улучшение flow регистрации (Вариант B) | `InviteController.java`, `invite/show.html`, `invite/list.html`, `register.html`, `login.html` | Запланировано, передано DeepSeek |

---

## Запланировано

| # | Задача | Приоритет | Примечания |
|---|---|---|---|
| 2 | Seed тестовых пользователей через `SEED_TEST_USERS=true` | Средний | `BootstrapApplicationRunner.java`, `.env`, `docker-compose.yml` |
| 3 | Автологаут через 5 минут бездействия | Средний | `SessionConfig.java` (300 сек), `_layout.html` (JS-таймер) |
| 4 | Обновить счётчики тестов в AI_IMPLEMENTATION_GUIDE.md (блоки 1–4) | Низкий | T1.9–11, T2.9–11, T3.9–11, T4.9–12 |
| 5 | `@MockBean` → `@MockitoBean` в UserProfileIntegrationTest | Низкий | Сломается в Spring Boot 3.5+ |
| 6 | R2 avatar URL — заглушка `/r2/...` вместо реального URL | Высокий (prod) | `UserProfileController.java:149` |

---

## Известные баги

| # | Баг | Где | Приоритет |
|---|---|---|---|
| 1 | Задублированные поля в `register.html` | `templates/register.html` | Высокий — войдёт в задачу #1 |
| 2 | T4.2 тест передаёт `"advanced"` вместо `"high"` | `ContentCreationIntegrationTest.java:185` | Средний |
| 3 | T4.6 тег-поиск — assertion failure (требует диагностики body) | `ContentCreationIntegrationTest.java:299` | Средний |

---

## Идеи (не запланировано)

- Замена `main.css` на Pico CSS или Tabler для более профессионального вида (после завершения функционала)
- Добавить тесты на недостающие сценарии из аудита блоков 1–4 (13 новых тестов)
- HikariPool warnings в тестах — настроить `maxLifetime` в `application.yml` для тестового профиля
