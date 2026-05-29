# Issue #9 — Выровнять кнопки статусов и «В избранное» по одной высоте

> ⚠️ Эту задачу **уже пытались решить** (Backlog #12 помечено «НЕ РЕШЕНО, несмотря на внесённые изменения»). Значит причина неочевидна — **не угадывать, а сначала воспроизвести и найти точную причину в DevTools** (computed styles), потом править.

## Что нужно

На странице активности и варианта группа кнопок статусов («✨ Интересно», «🎯 Хочу попробовать», …) и кнопка «🔖 В избранное» (+ кнопка жалобы) должны стоять **на одной горизонтальной линии**. Сейчас «Сохранить»/«В избранное» визуально выше кнопок статусов.

## Контекст

Обе страницы используют один блок-обёртку:
- [catalog/activity.html:60-74](src/main/resources/templates/catalog/activity.html#L60-L74)
- [catalog/variant.html:44-51](src/main/resources/templates/catalog/variant.html#L44-L51) (структура аналогична)

Разметка обёртки (activity.html):
```html
<div class="flex justify-between mt-4"
     style="border-top: ...; padding-top: 16px; align-items: flex-end; gap: 16px; flex-wrap: wrap;">
    <div th:replace="~{experience/_status_buttons :: statusButtons(...)}"></div>
    <div style="display: flex; align-items: center; gap: 8px; flex-shrink: 0;">
        <div th:replace="~{experience/_bookmark_button :: bookmarkButton(...)}"></div>
        <div th:replace="~{catalog/_report_button :: reportButton(...)}"></div>
    </div>
</div>
```

**Главный подозреваемый** — фрагмент статусов [experience/_status_buttons.html:7](src/main/resources/templates/experience/_status_buttons.html#L7): его форма имеет класс `flex gap-2 align-center **mt-4**`. Класс `mt-4` = `margin-top: 16px` ([main.css:445](src/main/resources/static/css/main.css#L445)) добавляет левому блоку вертикальный отступ сверху, которого нет у правого блока (bookmark/report). В сочетании с `align-items: flex-end` на обёртке это и даёт рассинхрон по вертикали.

> Примечание: правый блок — кнопка избранного — после фикса бага #32 (дубль `id`) станет `<form>` напрямую; на выравнивание это не влияет, но изменения согласовать, если обе задачи идут параллельно.

## Подход

1. **Воспроизвести** на `/activities/{id}`, открыть DevTools, выделить обёртку и оба дочерних блока, посмотреть computed `margin`, `height`, `align-items`. Подтвердить, что сдвиг даёт именно `mt-4` на форме статусов (или найти настоящую причину, если другая).

2. **Убрать `mt-4`** из формы в [experience/_status_buttons.html](src/main/resources/templates/experience/_status_buttons.html) — вертикальный отступ уже задаёт обёртка через `padding-top: 16px`. Класс там лишний и вреден (этот фрагмент вставляется только в эту обёртку).

3. На обёртке в [activity.html](src/main/resources/templates/catalog/activity.html) и [variant.html](src/main/resources/templates/catalog/variant.html) заменить `align-items: flex-end` на **`align-items: center`** — обе группы кнопок одной высоты (`btn-small`), по центру они выровняются стабильно и не зависят от случайных вертикальных отступов.

4. Перепроверить визуально: все кнопки на одной линии, на обеих страницах, при узком экране (когда блоки переносятся — `flex-wrap: wrap`) ничего не ломается.

## Узкие места

- `_status_buttons.html` используется и на `/saved`? — Нет, там только bookmark. Статусы — только activity/variant. Убирать `mt-4` безопасно.
- Не выносить инлайн-стили в новые CSS-классы без необходимости — минимальная правка. Но если правишь обёртку, делай это одинаково в обоих файлах.
- Не трогать сами фрагменты кнопок, кроме удаления `mt-4` (логика статусов/избранного не меняется).

## Что НЕ трогаем

- Backend, контроллеры, HTMX-логику.
- Кнопку жалобы.

## Как проверить

1. `/activities/{id}` — кнопки статусов и «В избранное» на одной линии.
2. `/activities/{aid}/variants/{vid}` — то же самое.
3. Сузить окно так, чтобы сработал перенос (`flex-wrap`) — раскладка остаётся аккуратной.
4. Поставить/снять статус и закладку — функциональность не сломана.
