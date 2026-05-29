# Issue #34 — Создание активности молча не срабатывает при некоторых значениях сложности/стоимости

## Что нужно

При создании активности с заполненными полями кнопка «Создать активность» иногда **молча не срабатывает**: нет ни перехода, ни ошибок/предупреждений. При тех же обязательных полях, но без изменения выпадашек — активность создаётся.

## Причина (две независимые проблемы)

### A. Рассинхрон опций `<select>` с enum'ами

Списки опций в контроллере не совпадают со значениями enum:

| Поле | Опции в `<select>` ([ActivityController:79-80](src/main/java/com/lep/portal/catalog/ActivityController.java#L79)) | Значения enum | Невалидные опции |
|---|---|---|---|
| Сложность | `beginner, medium, advanced` | `low, medium, high` ([ComplexityLevel](src/main/java/com/lep/portal/catalog/ComplexityLevel.java)) | **`beginner`, `advanced`** |
| Стоимость | `free, low, medium, high` | `free, low, mid, high` ([CostTier](src/main/java/com/lep/portal/catalog/CostTier.java)) | **`medium`** |

> 📐 Значения enum — это **подтверждённое архитектурное решение**: `complexity_level = {low, medium, high}` ([architecture_v1.md §9, open item 1](docs/architecture_v1.md); [data_model_v1.md §3, ENUM types](docs/data_model_v1.md)). `cost_tier = {free, low, mid, high}` там же. То есть источник истины — enum/БД-типы; опции `<select>` ошибочно отклонились от него. Менять нужно опции, не enum.

Если выбрать «сложность = beginner/advanced» или «стоимость = medium», Spring не может сконвертировать строку в enum-константу → `BindingResult.hasErrors() == true`.

Почему «при обязательных — работало»: не трогая выпадашки, форма шлёт **дефолты** `complexity=medium`, `costTier=free` ([CreateActivityForm:21-22](src/main/java/com/lep/portal/catalog/CreateActivityForm.java#L21)) — оба валидны.

### B. Ошибки валидации/конверсии не показываются (тихий фейл)

При `hasErrors()` контроллер делает `return "catalog/activity_form"` (HTTP 200, та же форма), но шаблон выводит ошибки **только для `title` и `categoryId`** ([activity_form.html:47,64](src/main/resources/templates/catalog/activity_form.html#L47)). Для `complexity`/`costTier` и прочих полей `th:errors` нет → форма молча перерисовывается, пользователь не видит причину.

## Подход

### Часть A — единый источник истины: опции из enum

Опции выпадашек **генерировать из enum**, а не дублировать строками. Заменить `List.of("beginner","medium","advanced")` и `List.of("free","low","medium","high")` на `ComplexityLevel.values()` / `CostTier.values()`.

⚠️ Эти списки продублированы в **четырёх** местах [ActivityController](src/main/java/com/lep/portal/catalog/ActivityController.java): `createForm` (:78-80), ветка ошибок `create` (:92-94), `editForm` (:157-159), ветка ошибок `update` (:172-174). Поправить **все четыре** (лучше — вынести в один `@ModelAttribute`-метод или приватный helper, кладущий `complexities`/`costTiers` из `values()`), иначе баг останется в редактировании.

> Метки в UI сейчас — это сами слаги (`low/medium/high`, `free/low/mid/high`). Если нужны человекочитаемые русские подписи — это отдельный косметический пункт; в рамках #34 достаточно, чтобы значения опций совпадали с enum. **Сами enum'ы не менять** — они соответствуют PostgreSQL-типам `complexity_level` / `cost_tier`.

### Часть B — сделать ошибки видимыми

1. В [activity_form.html](src/main/resources/templates/catalog/activity_form.html) добавить **общий блок ошибок** вверху формы (например `th:if="${#fields.hasErrors('*')}"` со списком `th:errors="*{*}"` / `#fields.allErrors()`), чтобы любая ошибка валидации/конверсии была видна. Дополнительно — по-полевой вывод для `complexity`/`costTier`.
2. В ветке `if (bindingResult.hasErrors())` [ActivityController](src/main/java/com/lep/portal/catalog/ActivityController.java) добавить логирование `bindingResult.getAllErrors()` — чтобы «тихие» отказы были видны в логах.

## Узкие места

- **Тот же рассинхрон в редактировании** (`editForm`/`update`) — обязательно покрыть.
- **Дефолты формы валидны** (`medium`/`free`) — поэтому баг проявляется не всегда; не обмануться «иногда работает».
- **enum ↔ БД:** значения enum совпадают с PostgreSQL-типами — менять опции под enum, не наоборот.
- Не сломать сохранение: `activity.setComplexity(form.getComplexity().name())` / `setCostTier(...)` — после фикса `.name()` вернёт валидный для БД слаг.

## Что НЕ трогаем

- Значения enum `ComplexityLevel` / `CostTier` (привязаны к типам БД).
- Логику создания/обновления и сохранения в репозиторий.

## Регрессионное тестовое покрытие

Автотесты на этот класс багов выносятся в отдельную задачу **#35** (`tasks/issue-35-form-validation-tests.md`) — там параметризованный инвариант «каждая опция каждого select принимается сервером». #35 выполнять после #34.

## Как проверить

1. Создать активность, **перебрав каждое** значение «Сложность» и «Стоимость» — во всех случаях активность создаётся (редирект на её страницу).
2. В выпадашках нет значений, которые сервер не принимает (`beginner`/`advanced` для сложности, `medium` для стоимости отсутствуют).
3. То же при **редактировании** активности.
4. При реальной ошибке валидации (например, пустое название) — на форме видно понятное сообщение, а не «тихая» перерисовка.
5. В логах при отказе формы видны конкретные ошибки биндинга.
