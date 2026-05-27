# Задача: Добавление активностей пользователями

**Issue:** #2
**Статус:** Запланировано

---

## Контекст и цель

Бэкенд для создания и редактирования активностей и вариантов **уже реализован**:
- `GET /activities/new` — форма создания (`activity_form.html`)
- `POST /activities` — сохранение (включая duplicate-warning)
- `GET /activities/{id}/edit` — форма редактирования (только автор/админ)
- `POST /activities/{id}` — обновление
- То же для вариантов: `/activities/{id}/variants/new`, `POST /activities/{id}/variants`, и т.д.

Проблема: в UI нет ни одной кнопки/ссылки, которая ведёт к этим страницам. Пользователь не знает, что может добавить активность.

**Что нужно сделать:** добавить точки входа в UI + страницу "Мои активности".

---

## Что делаем (без approval-флоу)

Согласно спецификации (`Life_Experience_Portal_Context_v3.md`, секция Roles & Permissions):
- Пользователь создаёт активность/вариант → статус сразу `ACTIVE`.
- Никакого шага "предложение → утверждение администратором" нет.

---

## 1. Кнопка "Предложить активность" в каталоге

**Файл:** `src/main/resources/templates/catalog/index.html`

Добавить кнопку над фильтрами (видна только аутентифицированным пользователям):

```html
<div class="flex justify-between align-center mb-4">
    <h1 class="page-title" style="margin-bottom: 0;">Каталог активностей</h1>
    <a sec:authorize="isAuthenticated()"
       href="/activities/new"
       class="btn btn-primary btn-small">
        + Предложить активность
    </a>
</div>
```

Убрать отдельный `<h1 class="page-title">` и встроить в flex-строку с кнопкой.
Нужен `xmlns:sec="http://www.thymeleaf.org/extras/spring-security"` в теге `<html>` (проверить — если уже есть, не дублировать).

---

## 2. Кнопки на странице активности

**Файл:** `src/main/resources/templates/catalog/activity.html`

### 2a. Кнопка "Редактировать" (для автора и администратора)

В блок хлебных крошек добавить кнопку справа:

```html
<nav class="breadcrumb text-muted mb-4 flex justify-between align-center" style="font-size: 0.85rem;">
    <div>
        <a href="/">Каталог</a> →
        <a th:href="@{/(category=${category.slug})}" th:text="${category.labelRu}">Категория</a> →
        <span th:text="${activity.title}">Активность</span>
    </div>
    <a th:if="${canEdit}"
       th:href="@{/activities/{id}/edit(id=${activity.id})}"
       class="btn btn-secondary btn-small">
        Редактировать
    </a>
</nav>
```

`canEdit` — передаётся из контроллера (см. п. 5).

### 2b. Кнопка "Добавить вариант" в секции вариантов

В блок вариантов добавить заголовок с кнопкой:

```html
<div class="card mt-4">
    <div class="flex justify-between align-center mb-3">
        <h2 style="font-size: 1.2rem; margin-bottom: 0;">Варианты</h2>
        <a sec:authorize="isAuthenticated()"
           th:href="@{/activities/{id}/variants/new(id=${activity.id})}"
           class="btn btn-secondary btn-small">
            + Добавить вариант
        </a>
    </div>
    <!-- существующий список вариантов -->
</div>
```

Блок должен показываться даже если вариантов нет (убрать `th:if="${!variants.isEmpty()}"` с этого div — перенести условие внутрь на список, а заголовок с кнопкой показывать всегда).

---

## 3. Страница "Мои активности"

Новая страница со списком активностей и вариантов, созданных текущим пользователем.

### 3a. Контроллер

**Файл:** `src/main/java/com/lep/portal/catalog/MyActivitiesController.java`

```java
@Controller
@RequestMapping("/my-activities")
public class MyActivitiesController {

    private final CatalogService catalogService;

    public MyActivitiesController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public String myActivities(Authentication auth, Model model) {
        UUID userId = ((PortalUserDetails) auth.getPrincipal()).getUserId();
        model.addAttribute("activities", catalogService.getActivitiesByUser(userId));
        // Для каждой активности нужны варианты — передаём Map<UUID, List<Variant>>
        List<Activity> activities = catalogService.getActivitiesByUser(userId);
        Map<UUID, List<Variant>> variantsByActivity = activities.stream()
            .collect(Collectors.toMap(
                Activity::getId,
                a -> (List<Variant>) catalogService.getVariantsByUser(userId).stream()
                    .filter(v -> v.getActivityId().equals(a.getId()))
                    .toList()
            ));
        // Отдельно — варианты к чужим активностям
        List<Variant> myVariants = catalogService.getVariantsByUser(userId);
        List<Variant> variantsOnOthers = myVariants.stream()
            .filter(v -> activities.stream().noneMatch(a -> a.getId().equals(v.getActivityId())))
            .toList();

        model.addAttribute("activities", activities);
        model.addAttribute("variantsByActivity", variantsByActivity);
        model.addAttribute("variantsOnOthers", variantsOnOthers);
        return "catalog/my_activities";
    }
}
```

### 3b. Метод в CatalogService

**Файл:** `src/main/java/com/lep/portal/catalog/CatalogService.java`

Добавить метод (репозиторий `findByCreatedBy` уже есть в `VariantRepository`):

```java
public List<Variant> getVariantsByUser(UUID userId) {
    return (List<Variant>) variantRepository.findByCreatedBy(userId);
}
```

### 3c. Шаблон

**Файл:** `src/main/resources/templates/catalog/my_activities.html`

```html
<!DOCTYPE html>
<html lang="ru" xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{_layout}">
<head><title>Мои активности</title></head>
<body>
<main layout:fragment="content" class="app-layout">

    <div class="flex justify-between align-center mb-4">
        <h1 class="page-title" style="margin-bottom: 0;">Мои активности</h1>
        <a href="/activities/new" class="btn btn-primary btn-small">+ Предложить активность</a>
    </div>

    <!-- Мои активности -->
    <div th:if="${activities.isEmpty()}" class="card text-muted text-center">
        <p>Вы ещё не создавали активности.</p>
    </div>

    <div th:each="activity : ${activities}" class="card mb-3">
        <div class="flex justify-between align-center">
            <h3 style="margin-bottom: 4px;">
                <a th:href="@{/activities/{id}(id=${activity.id})}"
                   th:text="${activity.title}"
                   style="color: var(--color-text); text-decoration: none;">
                    Название
                </a>
            </h3>
            <div class="flex gap-2">
                <span class="badge"
                      th:classappend="${activity.status.name() == 'ACTIVE'} ? 'badge-active' : 'badge-expired'"
                      th:text="${activity.status}">ACTIVE</span>
                <a th:href="@{/activities/{id}/edit(id=${activity.id})}"
                   class="btn btn-secondary btn-small">Редактировать</a>
            </div>
        </div>
        <p class="text-muted" style="font-size: 0.85rem; margin: 8px 0;"
           th:text="${#strings.abbreviate(activity.description, 150)}"></p>

        <!-- Варианты этой активности -->
        <div th:if="${variantsByActivity[activity.id] != null && !variantsByActivity[activity.id].isEmpty()}"
             style="margin-top: 8px; border-top: 1px solid var(--color-border); padding-top: 8px;">
            <span class="text-muted" style="font-size: 0.8rem;">Варианты:</span>
            <div th:each="v : ${variantsByActivity[activity.id]}"
                 class="flex justify-between align-center"
                 style="font-size: 0.85rem; padding: 4px 0;">
                <a th:href="@{/activities/{aid}/variants/{vid}(aid=${activity.id},vid=${v.id})}"
                   th:text="${v.title}"
                   style="color: var(--color-primary); text-decoration: none;">Вариант</a>
                <a th:href="@{/activities/{aid}/variants/{vid}/edit(aid=${activity.id},vid=${v.id})}"
                   class="btn btn-secondary btn-small">Редактировать</a>
            </div>
        </div>
        <div style="margin-top: 8px;">
            <a th:href="@{/activities/{id}/variants/new(id=${activity.id})}"
               class="btn btn-secondary btn-small">+ Добавить вариант</a>
        </div>
    </div>

    <!-- Варианты к чужим активностям -->
    <div th:if="${!variantsOnOthers.isEmpty()}" class="card mt-4">
        <h2 style="font-size: 1.1rem; margin-bottom: 12px;">Мои варианты к чужим активностям</h2>
        <div th:each="v : ${variantsOnOthers}"
             class="flex justify-between align-center mb-2"
             style="padding-bottom: 8px; border-bottom: 1px solid var(--color-border);">
            <a th:href="@{/activities/{aid}/variants/{vid}(aid=${v.activityId},vid=${v.id})}"
               th:text="${v.title}"
               style="color: var(--color-primary); text-decoration: none;">Вариант</a>
            <a th:href="@{/activities/{aid}/variants/{vid}/edit(aid=${v.activityId},vid=${v.id})}"
               class="btn btn-secondary btn-small">Редактировать</a>
        </div>
    </div>

</main>
</body>
</html>
```

---

## 4. Навигация

**Файл:** `src/main/resources/templates/_layout.html`

Добавить ссылку "Мои активности" в `<div class="nav-links">` после "Мой опыт":

```html
<a href="/my-activities">Мои активности</a>
```

---

## 5. Передача `canEdit` в контроллере активности

**Файл:** `src/main/java/com/lep/portal/catalog/ActivityController.java`

В методе `activityDetail` добавить вычисление и передачу `canEdit` в модель:

```java
boolean canEdit = currentUser != null &&
    (currentUser.isAdmin() || activity.getCreatedBy().equals(currentUser.getUserId()));
model.addAttribute("canEdit", canEdit);
```

---

## 6. Тесты

**Файл:** `src/test/java/com/lep/portal/catalog/MyActivitiesIntegrationTest.java` (новый)

Тест-сценарии:
- T1: Залогиненный пользователь открывает `/my-activities` → видит свои активности
- T2: Список пустой, если пользователь ничего не создавал
- T3: Варианты к чужим активностям показываются отдельным блоком
- T4: Кнопка "Редактировать" ведёт на `/activities/{id}/edit`
- T5: Незалогиненный пользователь → редирект на `/login`

---

## Затронутые файлы

| Файл | Действие |
|---|---|
| `templates/catalog/index.html` | + кнопка "Предложить активность" |
| `templates/catalog/activity.html` | + кнопка "Редактировать" + кнопка "Добавить вариант" |
| `templates/catalog/my_activities.html` | **новый** шаблон |
| `templates/_layout.html` | + ссылка "Мои активности" в навигацию |
| `catalog/MyActivitiesController.java` | **новый** контроллер |
| `catalog/CatalogService.java` | + метод `getVariantsByUser` |
| `catalog/ActivityController.java` | + передача `canEdit` в модель |
| `catalog/MyActivitiesIntegrationTest.java` | **новые** тесты |

---

## Что НЕ делаем в этой задаче

- Approval-флоу (предложение → утверждение): не предусмотрено спецификацией
- Удаление активностей из UI: только через БД или будущую задачу архивации
- Пагинация в "Мои активности": объём контента для одного пользователя невелик
