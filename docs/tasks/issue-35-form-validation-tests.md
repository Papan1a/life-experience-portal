# Issue #35 — Автотесты на «тихие» баги форм создания/редактирования контента

> Зависит от **#34** (`tasks/issue-34-create-activity-enum-mismatch.md`). Тесты должны быть зелёными **после** фикса #34; ключевой тест-инвариант до фикса падал бы — в этом и смысл (он ловит рассинхрон).

## Что нужно

Покрыть автотестами класс багов «форма молча не срабатывает» (сервер отклоняет запрос, а пользователь не видит причины). Триггер из #34 — рассинхрон опций `<select>` с enum'ами. Нужны тесты, которые ловят такие отказы автоматически и предотвращают регрессии.

## Контекст

- Контроллер: [ActivityController](src/main/java/com/lep/portal/catalog/ActivityController.java) — `POST /activities` (create), `POST /activities/{id}` (update). Успех = `3xx redirect`; отказ валидации = `200` с той же формой.
- Форма: [CreateActivityForm](src/main/java/com/lep/portal/catalog/CreateActivityForm.java); enum'ы [ComplexityLevel](src/main/java/com/lep/portal/catalog/ComplexityLevel.java) / [CostTier](src/main/java/com/lep/portal/catalog/CostTier.java).
- Существующие тесты: [ContentCreationIntegrationTest](src/test/java/com/lep/portal/ContentCreationIntegrationTest.java) (MockMvc, расширять здесь) — есть seed категорий/пользователя; CSRF включён (`with(csrf())`).
- Форма варианта: [CreateVariantForm](src/main/java/com/lep/portal/catalog/CreateVariantForm.java), `POST /activities/{id}/variants` (без enum-полей).

## Подход — тесты по уровням

### 1. 🎯 Тест-инвариант: каждая опция `<select>` принимается сервером (главный)

Параметризованный MockMvc-тест: для **каждого** значения `ComplexityLevel.values()` и **каждого** `CostTier.values()` отправить `POST /activities` с валидными обязательными полями → ожидать **3xx redirect** (а не 200 с формой). Это автоматически ловит любой будущий рассинхрон список↔enum.

- В идеале источник перебираемых значений — **тот же**, что контроллер кладёт в модель для `<select>` (после #34 это `values()`), чтобы тест проверял ровно то, что видит пользователь.

### 2. Happy path «все поля заполнены»

`POST /activities` со всеми полями (description, estimatedDuration, requirements, interestingReason, tags, валидные complexity/costTier) → `3xx redirect` + проверить, что активность создана в БД (по аналогии с существующими проверками `jdbcTemplate`).

### 3. Негативные кейсы (валидация видима)

- Пустой `title` → `200`, модель содержит ошибку поля `title` (`model().attributeHasFieldErrors(...)`).
- Невалидное значение enum (например, `complexity=advanced`) → отказ (после #34 такого значения в UI нет, но тест фиксирует серверный контракт — отклонять неизвестные enum).

### 4. Редактирование

`POST /activities/{id}` с каждым валидным значением enum → `3xx redirect`, изменения сохранены. (Та же логика рассинхрона была в `editForm`/`update`.)

### 5. (Опционально) Форма варианта

`POST /activities/{id}/variants` happy path → `3xx redirect` + запись в БД.

## Узкие места

- **CSRF:** в MockMvc использовать `with(csrf())`, иначе 403 (не путать с предметным отказом валидации).
- **Seed-данные:** нужна существующая категория (валидный `categoryId`) и аутентифицированный пользователь — переиспользовать setup из `ContentCreationIntegrationTest`.
- **Отличать 200-re-render от успеха:** именно проверка `status().is3xxRedirection()` ловит «тихий» баг; не ограничиваться `status().isOk()`.
- **Не дублировать #34:** здесь только покрытие тестами; сам фикс — в #34.
- HikariPool warnings в тестах — известная мелочь (см. BACKLOG «Идеи»), на результат не влияет.

## Что НЕ трогаем

- Прод-код (он чинится в #34). Если без минимальной правки прод-кода тест-инвариант не написать красиво — согласовать, но цель задачи — тесты.

## Как проверить

1. Тест-инвариант гоняет все значения `ComplexityLevel`/`CostTier` → все дают redirect.
2. Happy path «все поля» → redirect + запись в БД.
3. Негативные кейсы → 200 + видимая ошибка поля.
4. Тесты редактирования зелёные.
5. Вся сборка (`./mvnw test` или эквивалент) зелёная; счётчики тестов обновлены в `docs/AI_IMPLEMENTATION_GUIDE.md` (как принято в проекте).
