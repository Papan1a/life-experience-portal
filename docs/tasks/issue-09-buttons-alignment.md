# Issue #9 — Кнопки на странице активности: выравнивание по высоте

## Контекст

В `activity.html:53-63` три блока (кнопки статуса, кнопка "Сохранить", кнопка "Сообщить о проблеме") находятся в flex-контейнере с `justify-between`. Кнопки статуса — горизонтальная группа кнопок, закладка — одна кнопка, отчёт — `<details>` элемент. Из-за разной высоты элементов выравнивание нарушается.

## Файлы

- `src/main/resources/templates/catalog/activity.html`
- `src/main/resources/templates/experience/_bookmark_button.html`
- `src/main/resources/templates/catalog/_report_button.html`

## Что сделать

### В `activity.html` — выравнять контейнер

Заменить текущую структуру на flex с `align-items: flex-end`:

```html
<div class="flex justify-between mt-4"
     style="border-top: 1px solid var(--color-border); padding-top: 16px;
            align-items: flex-end; gap: 16px; flex-wrap: wrap;">
    <div th:replace="~{experience/_status_buttons :: statusButtons(...)}"></div>
    <div style="display: flex; align-items: center; gap: 8px; flex-shrink: 0;">
        <div th:replace="~{experience/_bookmark_button :: bookmarkButton(...)}"></div>
        <div th:replace="~{catalog/_report_button :: reportButton(...)}"></div>
    </div>
</div>
```

Кнопки закладки и отчёта объединить в один правый блок — они смысловые "вспомогательные действия" в отличие от кнопок статуса.

### В `_report_button.html` — убрать лишний отступ

Убрать `margin-top: 12px` с корневого `<div>` фрагмента — он смещал элемент вниз.

## Проверка

1. Открыть любую активность
2. Кнопки "Интересно/Хочу попробовать/..." на одном уровне с кнопкой "Сохранить"
3. Проверить на мобильной ширине (DevTools) — должен корректно переноситься через `flex-wrap: wrap`
