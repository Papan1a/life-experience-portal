# Issue #13 — Система жалоб: хранение в БД, админка и UI-модалка

Объединяет: исходный #13 (жалобы не сохраняются, админка-заглушка) + переработку UI жалобы (вынесена из блока статусов в #9 — нужно новое место).

## Что нужно

1. **Хранить жалобы в БД** (сейчас `ReportController` только пишет `log.warn` — [ReportController.java:35](src/main/java/com/lep/portal/admin/ReportController.java#L35)).
2. **Просмотр жалоб в админке** (сейчас `/admin/reports` — заглушка «будет доступна в следующей версии», [AdminController.java:101-105](src/main/java/com/lep/portal/admin/AdminController.java#L101)).
3. **Новый UI подачи жалобы** — всплывающее окно поверх страницы, не взаимодействующее с формой карточки:
   - модальное окно через нативный `<dialog>`;
   - комментарий ограничен **200 символами**;
   - **запрет resize** у поля комментария.

## Контекст (факты из кода)

- **Endpoint:** `POST /reports` ([ReportController.java:24](src/main/java/com/lep/portal/admin/ReportController.java#L24)), `@RestController`, возвращает текст «Спасибо…». Поля: `target_type`, `target_id` (UUID), `reason`, `comment` (optional). Причины валидируются по `VALID_REASONS = {duplicate, unsafe, spam, wrong_category, bad_description, other}` ([:21-22](src/main/java/com/lep/portal/admin/ReportController.java#L21)).
- **Текущая форма жалобы:** [_report_button.html](src/main/resources/templates/catalog/_report_button.html) — `<details>` с `hx-post=@{/reports}`, `hx-target="#report-result"`. `<textarea name="comment">` **без** `maxlength` и **без** `resize:none` (инлайн-стиль, не класс `.form-control`) — [:32-34](src/main/resources/templates/catalog/_report_button.html#L32). Resize сейчас разрешён.
- **Схема:** таблицы `reports` НЕТ (последняя миграция — `V6__invite_one_shot.sql`). Нужна новая `V7`.
- **CSRF** инжектится глобально в htmx-запросы ([_layout.html:146](src/main/resources/templates/_layout.html#L146)) — форма внутри `<dialog>` его получит автоматически.
- **`resize: none`** в проекте уже есть на `.form-control` ([main.css:181](src/main/resources/static/css/main.css#L181)) — переиспользовать класс либо задать `resize:none` явно.
- Паттерна модалки (`<dialog>`/popover) в проекте пока НЕТ — это первый; стек htmx 2.0.4 нативному `<dialog>` не мешает.

## Подход

### Часть 1 — хранение (бэкенд)

1. **Миграция `V7__reports.sql`** — таблица `reports`: `id` (UUID, по образцу прочих таблиц V1), `reporter_user_id` (FK users), `target_type TEXT NOT NULL CHECK (target_type IN ('activity','variant'))`, `target_id` (UUID), `reason` (text), `comment` (text, **nullable**), `status TEXT NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW','REVIEWED','RESOLVED','DISMISSED'))`, `created_at` (timestamptz default now()), `resolved_at` (timestamptz null), `resolved_by` (UUID null). Образец стиля колонок/индексов — `activities` в [V1__init.sql](src/main/resources/db/migration/V1__init.sql).
2. **Entity `Report` + `ReportRepository`** (Spring Data JDBC, по образцу `Invite`/`InviteRepository`).
3. **`ReportController.report`** — вместо `log.warn` сохранять жалобу через репозиторий. Сохранить контракт (поля те же), оставить валидацию `VALID_REASONS`, добавить:
   - **серверную проверку длины `comment` ≤ 200** (обрезка или 400 — выбрать; для UX предпочтительно обрезать и принять). Это защита: `maxlength` на фронте обходится.
   - `status='NEW'`, `reporter_user_id = principal.getUserId()`.

### Часть 2 — админка

4. **`AdminController.reports`** — загрузить реальные жалобы из репозитория (список, сортировка по `created_at DESC`), отдать в шаблон. Опционально — фильтр по `status`.
5. **Шаблон `admin/reports.html`** (отдельный файл, не секция в dashboard — dashboard и так разрастается). `AdminController.reports()` изменить return с `"admin/dashboard"` на `"admin/reports"` и убрать заглушку. Таблица жалоб: кто, на что (тип+ссылка на target), причина, комментарий, дата, статус. Действие «Отметить обработанной» → `POST /admin/reports/{id}/resolve` (ставит `status='RESOLVED'`, `resolved_at=now()`, `resolved_by`). По образцу admin-действий со статусами в [AdminController.java:47-78](src/main/java/com/lep/portal/admin/AdminController.java#L47).

### Часть 3 — UI-модалка (best practice: нативный `<dialog>`)

Почему `<dialog>` modal, а не popover: форма с обязательным выбором причины и отправкой = осознанное взаимодействие. `<dialog>` (`showModal()`) даёт встроенную роль `dialog`, ловушку фокуса, backdrop, закрытие по Esc и **делает фон inert** — то есть гарантированно «не взаимодействует с формой карточки» (ровно требование владельца). Popover API для эфемерных всплывашек без обязательного ввода — не наш случень.

6. Переписать [_report_button.html](src/main/resources/templates/catalog/_report_button.html): вместо `<details>` — **триггер** (ссылка/кнопка «⚠ Сообщить о проблеме») + `<dialog>` с формой жалобы внутри.
   - **`id` диалога** — сгенерировать из `targetId`: `th:id="'report-dialog-' + ${targetId}"`. Триггер открывает именно этот диалог, не захардкоженный `#report-dialog`. Это защита от дублей `id` при возможном появлении фрагмента в списках.
   - Открытие: `document.getElementById('report-dialog-' + targetId).showModal()`; закрытие: кнопка «Отмена» + Esc (нативно) + клик по backdrop.
   - Форма внутри — тот же `hx-post=@{/reports}`; `hx-target` указывает на элемент результата **внутри этого же `<dialog>`** (не глобальный id).
   - **Закрытие после ответа:** на форму навесить `addEventListener('htmx:afterSwap', () => setTimeout(() => dialog.close(), 2000))`. Не использовать `<script>` в теле ответа сервера.
   - `<textarea name="comment" maxlength="200">` + **`resize: none`** (класс `.form-control` или явный стиль). Желательно счётчик «N/200».
   - Корректный HTML: `<dialog>` на уровне страницы/фрагмента, **не вложен** в форму карточки/блок статусов.
7. **Вынос из блока статусов уже сделан в #9** — здесь определить финальное место триггера жалобы (например, в шапке карточки активности/варианта или отдельной строкой). Согласовать с #9.

## Узкие места

- **`<dialog>` + повторяемость:** если фрагмент жалобы рендерится в списках (каталог/`/saved`) — у каждого свой `<dialog>` с уникальным `id` (иначе дубль `id`, как было в #32). На страницах деталей (activity/variant) фрагмент один — проблемы нет. Уточнить, нужна ли жалоба в каталоге вообще, или только на детальных страницах.
- **Лимит 200 — на фронте И на бэке.** `maxlength` только UI; серверная проверка обязательна.
- **htmx внутри `<dialog>`:** `hx-target` должен указывать на элемент внутри того же `<dialog>` (результат/закрытие), не на глобальный `id`.
- **Контракт `/reports` не ломать без нужды** — поля `target_type/target_id/reason/comment` сохранить, чтобы не трогать места вызова сверх необходимого.
- **`target_type`** сейчас приходит строкой 'activity'/'variant' из фрагмента ([_report_button.html:13](src/main/resources/templates/catalog/_report_button.html#L13)) — сохранить совместимость со схемой.

## Что НЕ трогаем

- Список `VALID_REASONS` (если не просили менять причины).
- Логику статусов/избранного, фрагменты `_status_buttons`/`_bookmark_button`.
- Spring Security.

## Как проверить

1. Подать жалобу → запись появляется в таблице `reports` (проверить в БД).
2. `/admin/reports` → жалоба видна в списке; «Отметить обработанной» меняет `status` на RESOLVED.
3. Кнопка «Сообщить о проблеме» открывает модальное окно **поверх** страницы; фон неактивен (inert); Esc/Отмена/клик по фону закрывают.
4. Поле комментария **нельзя растянуть** (resize запрещён); ввод ограничен 200 символами (и обрезается/отклоняется на сервере при обходе).
5. Окно жалобы **не влияет** на форму статусов/избранного карточки.
6. На узком экране модалка корректна; доступность с клавиатуры (фокус внутри окна, Esc).
