# Issue #1 — Удаление своих активностей и вариантов

## Что нужно

У активностей и вариантов есть кнопки «Редактировать», но **нет возможности удалить** созданный пользователем контент. Добавить удаление — доступное автору (и админу).

## Контекст (важно: инфраструктура soft-delete уже есть)

Схема БД **уже использует soft-delete** через колонку `deleted_at`:
- [ActivityRepository:94](src/main/java/com/lep/portal/catalog/ActivityRepository.java#L94) — `findByCreatedBy` уже фильтрует `WHERE created_by = :userId AND deleted_at IS NULL`.
- Каталог читает из view `visible_activities` / `visible_variants` (см. [ActivityRepository:15](src/main/java/com/lep/portal/catalog/ActivityRepository.java#L15)) — почти наверняка эти view уже отсекают `deleted_at IS NULL` (и/или статус). **Проверить определение view** в миграциях (`src/main/resources/db/migration`), чтобы понять точное условие видимости.

Значит «удаление» = проставить `deleted_at = now()` (мягкое удаление). Это безопаснее жёсткого `DELETE`: не ломает FK от `bookmarks`, `user_experiences`, тегов и вариантов, и сохраняет историю.

Существующая логика прав (переиспользовать паттерн):
- [CatalogService.updateActivity:105-122](src/main/java/com/lep/portal/catalog/CatalogService.java#L105-L122) и `updateVariant:131-144` — проверка `created_by == actor || isAdmin`, иначе `ForbiddenException`.
- Контроллеры: [ActivityController](src/main/java/com/lep/portal/catalog/ActivityController.java), [VariantController](src/main/java/com/lep/portal/catalog/VariantController.java).

UI-точки, где есть «Редактировать» (рядом добавить «Удалить»):
- [activity.html:19-23](src/main/resources/templates/catalog/activity.html#L19-L23) (хлебные крошки, `th:if="${canEdit}"`).
- [variant.html](src/main/resources/templates/catalog/variant.html) (аналогичная кнопка редактирования варианта).
- [my_activities.html:35-36](src/main/resources/templates/catalog/my_activities.html#L35-L36) и `:52-53` (списки своих активностей/вариантов).

## Подход

### 1. Проверить схему
- Найти в миграциях определение `visible_activities` / `visible_variants` и колонку `deleted_at` у `activities` и `variants`. Убедиться, что view скрывает удалённые. Если у `variants` нет `deleted_at` — добавить миграцию с колонкой и обновить view (по образцу activities).
- Убедиться, что entity [Activity](src/main/java/com/lep/portal/catalog/Activity.java) / [Variant](src/main/java/com/lep/portal/catalog/Variant.java) имеют поле `deletedAt` (если нет — добавить с маппингом на колонку).

### 2. Сервис
В [CatalogService](src/main/java/com/lep/portal/catalog/CatalogService.java) добавить:
```
public void deleteActivity(UUID id, UUID actorId, boolean isAdmin)
public void deleteVariant(UUID id, UUID actorId, boolean isAdmin)
```
- Проверка прав — тот же паттерн, что в `updateActivity` (автор или админ, иначе `ForbiddenException`).
- Проставить `deletedAt = Instant.now()` и сохранить (или через `@Modifying`-запрос в репозитории — по стилю проекта; в проекте используется `save(existing)`).
- **Каскад для активности:** при удалении активности её варианты тоже должны исчезнуть из выдачи. Варианты: либо пометить `deleted_at` у всех вариантов этой активности, либо положиться на то, что view вариантов скрывает варианты удалённых активностей. **Выбрать и зафиксировать** один способ; предпочтительно явно проставлять `deleted_at` вариантам (предсказуемо).

### 3. Контроллеры (эндпоинты)
- `ActivityController`: `@PostMapping("/{id}/delete")` → `deleteActivity(...)` → `redirect:/my-activities` (с flash-сообщением «Активность удалена»).
- `VariantController`: `@PostMapping("/{variantId}/delete")` → `deleteVariant(...)` → `redirect:/activities/{activityId}` или `/my-activities`.
- Использовать **POST**, не GET (удаление — изменяющая операция; CSRF-токен обязателен, в проекте CSRF включён).

### 4. UI
Рядом с «Редактировать» добавить кнопку/форму «Удалить» (только при `canEdit` / для своих в `my_activities`):
```html
<form th:action="@{/activities/{id}/delete(id=${activity.id})}" method="post"
      onsubmit="return confirm('Удалить активность? Это действие необратимо.');"
      style="display:inline">
    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
    <button type="submit" class="btn btn-small" style="color: var(--color-error);">Удалить</button>
</form>
```
- Обязательно **подтверждение** перед удалением (`confirm`).
- На странице активности `canEdit` уже вычисляется ([ActivityController:53-55](src/main/java/com/lep/portal/catalog/ActivityController.java#L53)). Для варианта — добавить аналогичный `canEdit` в модель `variantDetail` (сейчас его там нет — проверить и добавить, иначе кнопку негде условно показать).

## Узкие места

- **Права:** кнопку показывать только автору/админу, но проверку прав делать **на сервере** (UI-условие — не защита). Сервис уже бросает `ForbiddenException`.
- **Каскад и FK:** именно поэтому выбран soft-delete — не трогаем строки в `bookmarks`/`user_experiences`. Жёсткий `DELETE` потребовал бы каскадов и риск осиротевших ссылок. **Не делать hard delete.**
- **Видимость после удаления:** удалённое не должно появляться в каталоге, «Мои активности», «Избранное», «Мой опыт», на странице деталей (должна отдавать 404 через `getVisibleActivity`). Проверить все списки.
- `variantDetail` сейчас не кладёт `canEdit` в модель — для кнопки удаления варианта на его странице это нужно добавить.

## Что НЕ трогаем

- Логику создания/редактирования.
- Жалобы, статусы, закладки (они переживают soft-delete нетронутыми).

## Как проверить

1. Автор видит «Удалить» у своей активности/варианта; у чужих — не видит.
2. Удаление активности → редирект, активность пропала из каталога, «Мои активности», деталей (404), и её варианты тоже скрыты.
3. Удаление варианта → пропал со страницы активности и из списков, сама активность цела.
4. Прямой POST на удаление чужого контента не-автором → 403 (ForbiddenException).
5. Закладки/опыт других пользователей на удалённый контент не вызывают ошибок на их страницах.
