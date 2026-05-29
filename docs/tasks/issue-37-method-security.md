# Issue #37 — `@PreAuthorize` неактивна: включить method security (или убрать аннотацию)

## Что нужно

В проекте используется `@PreAuthorize`, но method-level security **не включена** → аннотация молча игнорируется. Привести в порядок: либо активировать method security (defense-in-depth), либо убрать вводящую в заблуждение аннотацию.

## Контекст

- [AdminController:24](src/main/java/com/lep/portal/admin/AdminController.java#L24) помечен `@PreAuthorize("hasRole('ADMIN')")`.
- В проекте **нет** `@EnableMethodSecurity` (и `@EnableGlobalMethodSecurity`) — проверено по всему `src/main/java`. Значит `@PreAuthorize` **не применяется**.
- Сейчас админку фактически защищает только route-правило в [SecurityConfig:40](src/main/java/com/lep/portal/config/SecurityConfig.java#L40): `/admin/** → hasRole("ADMIN")`. Оно работает, поэтому дыры в доступе **сейчас нет** — но `@PreAuthorize` создаёт ложное ощущение method-level защиты: любой будущий админ-эндпоинт **вне** пути `/admin/**`, понадеявшийся на аннотацию, окажется незащищён.

## Подход (выбрать один)

### Вариант A — включить method security (рекомендуется, defense-in-depth)
- Добавить `@EnableMethodSecurity` на `@Configuration`-класс (например, [SecurityConfig](src/main/java/com/lep/portal/config/SecurityConfig.java)).
- Тогда `@PreAuthorize("hasRole('ADMIN')")` начнёт реально работать — двойная защита (route + метод).
- **Проверить, что роль соответствует:** `hasRole('ADMIN')` ожидает authority `ROLE_ADMIN`. Убедиться, что [PortalUserDetails](src/main/java/com/lep/portal/user/PortalUserDetails.java)/[PortalUserDetailsService](src/main/java/com/lep/portal/user/PortalUserDetailsService.java) выдаёт именно `ROLE_ADMIN` (а не `ADMIN`), иначе после включения админ-методы начнут отдавать 403.

### Вариант B — убрать аннотацию
- Удалить `@PreAuthorize` из `AdminController`, положившись на route-level правило. Меньше «магии», но и без method-level защиты на вырост.

**Рекомендация — A:** включить `@EnableMethodSecurity` и оставить аннотацию; route-правило в SecurityConfig сохранить как внешний рубеж.

## Узкие места

- 🔴 **Соответствие роли** (`ROLE_ADMIN` vs `ADMIN`) — главный риск варианта A: при рассинхроне включение method security **сломает** доступ админа (403). Проверить выдачу authorities перед включением.
- **Просканировать весь проект** на `@PreAuthorize`/`@Secured`/`@PostAuthorize` — при включении они все станут активны; убедиться, что нигде нет аннотации, которая внезапно что-то заблокирует.
- **Тесты** ([AdminIntegrationTest](src/test/java/com/lep/portal/AdminIntegrationTest.java)): прогнать — админ-доступ и запрет для обычного пользователя должны работать; при необходимости добавить тест «обычный пользователь → 403 на админ-действие».
- Не убирать route-level правило `/admin/**` (это внешний рубеж защиты).

## Что НЕ трогаем

- Логику аутентификации, Argon2, CSRF, сессии.
- Бизнес-логику админ-действий (это #36).

## Как проверить

1. Админ выполняет админ-действия (`/admin`, archive/block/activate, rename tag) — успешно.
2. Обычный пользователь, обратившийся к админ-эндпоинту, получает 403/redirect (и через route-правило, и через `@PreAuthorize`, если выбран A).
3. (Вариант A) `@EnableMethodSecurity` присутствует; authority — `ROLE_ADMIN`; ничего постороннего не заблокировано.
4. Сборка и тесты зелёные.
