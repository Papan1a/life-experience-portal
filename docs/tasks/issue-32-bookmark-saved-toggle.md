# Issue #32 — Баг: на `/saved` отметка «В избранном» меняется не у той карточки

## Что нужно

На странице «Избранное» (`/saved`) клик по кнопке «🔖 В избранном» в любой карточке визуально меняет кнопку **в первой** карточке списка, а не в той, по которой кликнули. (На сервере отметка снимается с правильной записи — баг чисто визуальный/фронтовый.)

## Причина

Фрагмент кнопки [experience/_bookmark_button.html:3](src/main/resources/templates/experience/_bookmark_button.html#L3) имеет **захардкоженный** `id="bookmark-button"`, а форма таргетит `hx-target="#bookmark-button"` ([:6](src/main/resources/templates/experience/_bookmark_button.html#L6)).

На странице активности/варианта фрагмент один — работает. На `/saved` он рендерится в цикле `th:each` ([saved.html:48](src/main/resources/templates/experience/saved.html#L48)) → на странице **N элементов с одинаковым `id="bookmark-button"`**. HTMX резолвит `#bookmark-button` через `querySelector`, который возвращает **первый** элемент в DOM. Поэтому ответ всегда подменяет первую карточку. Плюс невалидный HTML (дублирующийся `id`).

## Подход — `<form>` как корень фрагмента + `hx-target="this"`

Наивный перенос `hx-target="this"` на форму внутри `<div id="bookmark-button">` **не годится**: сервер возвращает корень фрагмента (`<div>`), а `hx-target="this"` на форме с `outerHTML` заменил бы `<form>` на `<div><form>` → вложенность ломается после второго клика. Поэтому корнем фрагмента делаем саму форму.

Переписать [experience/_bookmark_button.html](src/main/resources/templates/experience/_bookmark_button.html):

```html
<form th:fragment="bookmarkButton(activityId, variantId, bookmark)"
      class="bookmark-button"
      th:attr="hx-post=@{/bookmarks/toggle}"
      hx-swap="outerHTML"
      hx-target="this">
    <input type="hidden" name="activityId" th:value="${activityId}"/>
    <input type="hidden" name="variantId" th:value="${variantId}"/>
    <button type="submit"
            class="btn btn-small"
            th:classappend="${bookmark != null} ? 'btn-primary' : 'btn-secondary'">
        <th:block th:if="${bookmark != null}">🔖 В избранном</th:block>
        <th:block th:unless="${bookmark != null}">🔖 В избранное</th:block>
    </button>
</form>
```

Ключевое:
1. **`th:fragment` переносится на `<form>`**, внешний `<div id="bookmark-button">` удаляется.
2. **`hx-target="this"`** — форма заменяет сама себя; глобальный `id` больше не нужен, дубль исчезает.
3. **`class="bookmark-button"`** (не `id`) — сохраняем якорь `bookmark-button` как **класс** (классы дублироваться можно, HTML валиден). Это нужно, чтобы не сломать тесты (см. узкие места), и даёт CSS-хук на будущее.

## Узкие места

- 🔴 **Тесты завязаны на подстроку `"bookmark-button"`** — если просто удалить `id`, упадут:
  - [ExperienceIntegrationTest.java:277](src/test/java/com/lep/portal/ExperienceIntegrationTest.java#L277) (T5.5) — `containsString("bookmark-button")`
  - [EndToEndTest.java:334](src/test/java/com/lep/portal/EndToEndTest.java#L334) (E2E.5) — то же
  Сохранение `bookmark-button` как **класса** оставляет обе проверки зелёными без правки тестов. (Опционально можно усилить ассерты до `hx-post="/bookmarks/toggle"`, но не обязательно.)
- **`th:replace` на страницах-потребителях** заменяет host-элемент целиком: `<div th:replace=...>` в [saved.html:48](src/main/resources/templates/experience/saved.html#L48), [activity.html:68](src/main/resources/templates/catalog/activity.html#L68), [variant.html:48](src/main/resources/templates/catalog/variant.html#L48) станет `<form>`. Родительские flex-обёртки сохраняются, вёрстка не ломается. ✅ Проверено.
- **CSS:** селекторов `#bookmark-button` в `static/` нет — CSS-регрессии не будет. ✅ Проверено.
- `HtmxTemplateRenderingTest:158` ассертит `hx-post="/bookmarks/toggle"` — сохраняется. ✅

## Что НЕ трогаем

- Backend: [BookmarkController.toggleBookmark](src/main/java/com/lep/portal/experience/BookmarkController.java#L30) по-прежнему возвращает `experience/_bookmark_button :: bookmarkButton` (теперь это `<form>`).
- Логику избранного, сервис, репозиторий.
- Фрагмент статусов и кнопку жалобы.

## Как проверить

1. `/saved`: клик по «В избранном» в любой карточке меняет кнопку **именно в этой** карточке, а не в первой.
2. Страница активности (`/activities/{id}`) — избранное по-прежнему работает.
3. Страница варианта (`/activities/{aid}/variants/{vid}`) — избранное работает.
4. Тесты `ExperienceIntegrationTest` (T5.5) и `EndToEndTest` (E2E.5) — зелёные.
5. Вёрстка карточек на `/saved` и блоков на страницах активности/варианта не сломана.
