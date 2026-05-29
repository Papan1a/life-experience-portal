# Issue #36 — Переходы статусов в сервис + унификация дублей (admin/catalog)

## Что нужно

Устранить нарушение слоистости и дубли, найденные при архитектурном аудите:
1. Переходы статусов активностей/вариантов должны быть **операциями сервиса**, а не выполняться в контроллере.
2. Унифицировать нормализацию slug тегов (сейчас две расходящиеся реализации).
3. Вынести дублирующийся `parseTags`.
4. Привести обработку «не найдено» в админке к доменному исключению.

## Контекст

- **Нарушение принципа.** [architecture_v1.md §4](docs/architecture_v1.md): *«Status transitions are explicit service operations.»* Но [AdminController:47-81](src/main/java/com/lep/portal/admin/AdminController.java#L47-L81) делает `setStatus("ARCHIVED"/"BLOCKED"/"ACTIVE") + repository.save()` **прямо в контроллере**, через `activityRepository`/`variantRepository`, минуя [CatalogService](src/main/java/com/lep/portal/catalog/CatalogService.java).
- **Slug — две реализации:** [AdminController.normalizeSlug:107-111](src/main/java/com/lep/portal/admin/AdminController.java#L107) (regex, пробелы→`_`) против [CatalogService.findOrCreateTag:202-211](src/main/java/com/lep/portal/catalog/CatalogService.java#L202) (`name.toLowerCase().trim()`). Дают **разный** slug для одного имени → тег, созданный одним путём, может не находиться другим.
- **`parseTags`** — идентичный приватный метод в [ActivityController:194-202](src/main/java/com/lep/portal/catalog/ActivityController.java#L194) и [VariantController:177-185](src/main/java/com/lep/portal/catalog/VariantController.java#L177).
- **«Не найдено»:** AdminController использует `findById(id).orElseThrow()` (→ `NoSuchElementException` → 500), тогда как остальной код кидает доменный [NotFoundException](src/main/java/com/lep/portal/common/NotFoundException.java) (→ 404).

## Подход

### 1. Статус-переходы в `CatalogService`
Добавить явные методы, инкапсулирующие смену статуса + сохранение (по образцу `updateActivity`/`updateVariant`):
```
public Activity changeActivityStatus(UUID id, ActivityStatus newStatus)   // или archive/block/activate
public Variant  changeVariantStatus(UUID id, VariantStatus newStatus)
```
- Внутри — загрузка агрегата, смена статуса, `save`. При желании — валидация допустимости перехода (например, нельзя `BLOCKED → ACTIVE` минуя проверку); по согласованию, можно начать без строгой матрицы переходов.
- `AdminController` вызывает сервис вместо прямой работы с репозиторием.

> ⚠️ **Связано с #1** (`tasks/issue-1-delete-content.md`): там в `CatalogService` добавляются `deleteActivity/deleteVariant` (soft-delete). Делать согласованно — и удаление, и статус-переходы должны жить в сервисе рядом. Желательно выполнять #36 вместе с #1 или сразу после.

### 2. Единая нормализация slug
Вынести нормализацию в **один** метод (в `CatalogService` или новом `TagService`), переиспользовать его и в `findOrCreateTag`, и в `AdminController.renameTag`. Выбрать каноническое правило (рекомендуется `lower(trim(name))` — оно уже у `findOrCreateTag` и совпадает с data-model «slug = normalized lower(trim(name))», [data_model_v1.md §4](docs/data_model_v1.md)). Привести обе точки к нему.

### 3. `parseTags` — в одно место
Вынести в `CatalogService` (или util) и переиспользовать в обоих контроллерах.

### 4. Единый `NotFoundException` в admin
Заменить `findById().orElseThrow()` на загрузку через сервис (`getActivity`/`getVariant`, которые уже кидают `NotFoundException`).

### (Опционально, низкий приоритет)
Детальные контроллеры [ActivityController](src/main/java/com/lep/portal/catalog/ActivityController.java)/[VariantController](src/main/java/com/lep/portal/catalog/VariantController.java) читают `UserExperience`/`Bookmark` напрямую из репозиториев для рендера. Можно завести читающие методы в соответствующих сервисах. Не обязательно в рамках #36 — это чтение, не бизнес-логика.

## Узкие места

- **Смена правила slug** может изменить slug для тегов с пробелами/регистром — для существующих данных это, как правило, безвредно (slug пересчитывается при сохранении), но проверить, что `renameTag` после унификации не создаёт коллизий с уникальным `slug`.
- **Тесты админки** ([AdminIntegrationTest](src/test/java/com/lep/portal/AdminIntegrationTest.java)) — прогнать после рефактора; статус-переходы должны работать как раньше.
- Не вводить строгую матрицу переходов, если она ломает текущие сценарии админки — согласовать объём валидации.

## Что НЕ трогаем

- Значения enum статусов (`ActivityStatus`/`VariantStatus`) и их соответствие БД-типам.
- Шаблоны админки (только при необходимости — вызовы остаются те же URL).
- Route-level правила безопасности (это #37).

## Как проверить

1. Архивирование/блокировка/активация активности и архивирование варианта в админке работают через `CatalogService` (статус меняется, редирект, флеш-сообщение).
2. Тег, созданный при создании активности, и тег, переименованный в админке, дают **одинаковый** slug по одному правилу; поиск/автодополнение находят их одинаково.
3. `parseTags` — единственная реализация, оба контроллера используют её.
4. Запрос несуществующего id в админ-действии → 404 (`NotFoundException`), а не 500.
5. Сборка и тесты зелёные.
