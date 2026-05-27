# Issue #11a и #11b — Профиль: убрать "Био не заполнено", показать email

## Файлы

- `src/main/resources/templates/user/profile.html`

## 11a — Убрать "Био не заполнено"

В `user/profile.html:26` удалить строку:

```html
<p th:unless="${profileUser.bio}" class="text-muted">Био не заполнено</p>
```

Если bio пустое — просто ничего не показывать. Пустое место чище чем бессмысленная подсказка.

## 11b — Показывать email пользователя

Добавить отображение email **только для собственного профиля** (чужой email — приватная информация).

В секцию bio после удалённой строки 26, добавить:

```html
<div th:if="${isOwnProfile}" class="text-muted mt-2" style="font-size: 0.9rem;">
    <span>📧 </span>
    <span th:text="${profileUser.email}">email@example.com</span>
</div>
```

`isOwnProfile` уже передаётся контроллером — менять Java-код **не нужно**.

## Проверка

1. Открыть свой профиль — email виден, "Био не заполнено" исчезло
2. Открыть чужой профиль — email **не виден**
3. Если bio заполнено — отображается как раньше
