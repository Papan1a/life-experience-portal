# Issue #19 — Выбор аватара из предложенных + отображение

## Что нужно

В настройках профиля — возможность **выбрать аватар из набора предложенных** (готовых картинок). Выбор закрепляется за пользователем и отображается в разных местах (сейчас в фокусе — **топ-бар** и **профиль**).

## Решения, принятые при обсуждении

- **«Окошко онлайн»** из формулировки issue — это **отдельный будущий виджет**, которого ещё нет. Поэтому #19 **не трогает** блок «Что делают другие» и `FriendActivityDTO`.
- **Хранение выбора (ВАЖНО — модель):** в `users.avatar_url` для пресета храним **логический ключ с префиксом** `preset:{key}` (например `preset:cat`), а **не** готовый путь к файлу. Полный URL картинки собирает кодовый слой-резолвер из ключа + базового адреса из конфигурации (env). Это решает хрупкость: при переезде картинок/смене CDN меняется **одна env-переменная**, данные в БД не трогаются.
- **Сосуществование с загрузкой (#12):** поле `avatar_url` делится между пресетами, загруженными фото (R2) и дефолтом. Различаем по самоописываемому значению:
  - `preset:{key}` → пресет (собрать URL из env);
  - готовый URL (`http...` / `/...`) → загруженное фото R2 / legacy (отдать как есть);
  - `null` → дефолт.
- **Не зависит от R2/#7:** пресеты — статика, функция работает без настроенного хранилища.
- **Топ-бар:** аватар текущего юзера прокидывается глобальным `@ControllerAdvice` + `@ModelAttribute`, с **обязательным кэшем** (см. шаг 6).
- **Картинки-пресеты поставляет владелец** (кладёт файлы в `static/images/avatars/`); имена файлов = ключам whitelist.

## Контекст (текущее состояние)

- Хранение: `users.avatar_url` (String); [UserService.updateAvatar](src/main/java/com/lep/portal/user/UserService.java) уже есть.
- Профиль: [user/profile.html](src/main/resources/templates/user/profile.html) / [profile.html](src/main/resources/templates/profile.html) сейчас показывают `${...avatarUrl} ?: default` **напрямую** — после смены модели обязаны идти через резолвер (см. узкие места).
- **Топ-бар [_layout.html:87](src/main/resources/templates/_layout.html#L87)** — хардкод `@{/images/avatar-default.png}` (не показывает реальный аватар). ❌ чинить.
- Принципал [PortalUserDetails](src/main/java/com/lep/portal/user/PortalUserDetails.java) — `avatarUrl` не содержит (и не требуется при варианте с `@ControllerAdvice`).
- Профиль-редактирование: [user/edit.html](src/main/resources/templates/user/edit.html) — есть форма загрузки файла (`POST /profile/avatar`); галереи пресетов нет.
- Загрузка файла: [UserProfileController.uploadAvatar](src/main/java/com/lep/portal/user/UserProfileController.java#L114) — пишет в `avatar_url` готовый URL (это остаётся как есть, см. сосуществование).
- Файлы: сейчас только `default-avatar.svg` и `avatar-default.png` — **набора пресетов нет**.

## Подход

### 1. Набор пресетов (файлы + whitelist + базовый адрес)
- Владелец кладёт квадратные PNG в `src/main/resources/static/images/avatars/` (рекомендуется 256×256, единый стиль). Имя файла = `{key}.png`.
- **Whitelist ключей** — в `application.yml`:
  ```yaml
  app:
    avatar-presets: [cat, dog, fox, owl, panda, robot, cactus, mountain]
    # базовый адрес расположения картинок-пресетов; меняется одной env-переменной при переезде/CDN
    avatar-preset-base-url: ${AVATAR_PRESET_BASE_URL:/images/avatars/}
  ```
  Список и базовый URL читаются в конфиг-бин (`@ConfigurationProperties`).
- Whitelist нужен и для галереи, и как **защита**: эндпоинт принимает только `key` из списка.

### 2. Резолвер URL (кодовый слой — ключевой компонент)
Создать бин `AvatarUrlResolver` с методом `resolve(String rawAvatarUrl) -> String`:
```
resolve(raw):
  if raw is null/blank          -> defaultAvatarUrl            // дефолт
  if raw startsWith "preset:"   -> presetBaseUrl + key + ".png" // ключ + env
  else                          -> raw                          // готовый URL (R2/legacy)
```
- `presetBaseUrl` и `defaultAvatarUrl` — из конфига (env).
- Используется **во всех** местах показа аватара: топ-бар (через advice), профиль, галерея edit. В Thymeleaf вызывается как бин: `th:src="${@avatarUrlResolver.resolve(user.avatarUrl)}"`.
- Резолвер чистый и дешёвый (строка + конфиг), без обращения к БД.

> При #38 (`AvatarService`) резолвер логично объединить с сервисом аватара — единая точка построения URL.

### 3. Сервис выбора пресета
- Метод: проверить `key ∈ whitelist` → `userService.updateAvatar(userId, "preset:" + key)`.
  (В БД оседает `preset:cat`, **не** путь.)
- Размещение: пока рядом с логикой профиля; при #38 переедет в `AvatarService`.

### 4. Контроллер
- `POST /profile/avatar/preset` с параметром `key` → валидация по whitelist → выбор → `redirect:/profile/edit` с flash-сообщением.
- CSRF-токен обязателен.

### 5. UI — галерея в `user/edit.html`
- Секция «Выбрать готовый аватар»: `th:each` по whitelist; каждая картинка — маленькая форма (`POST /profile/avatar/preset`, hidden `key`, кнопка-картинка).
- Превью каждого пресета строить через резолвер/базовый URL: `@{...presetBaseUrl + key + '.png'}` (или `${@avatarUrlResolver.resolve('preset:' + key)}`).
- **Текущий** выбранный пресет выделить, сравнивая **по ключу**: `user.avatarUrl == 'preset:' + key` (а не по пути).
- Рядом оставить существующую загрузку своего файла (два способа).

### 6. Отображение в топ-баре (с ОБЯЗАТЕЛЬНЫМ кэшем)
- `@ControllerAdvice` c `@ModelAttribute("currentAvatarUrl")`: для аутентифицированного пользователя берёт `avatar_url` из БД, прогоняет через `AvatarUrlResolver.resolve(...)`, возвращает готовый URL; для анонима — `null`/дефолт (null-safe).
- В [_layout.html](src/main/resources/templates/_layout.html#L87) заменить хардкод на `th:src="${currentAvatarUrl}"` (резолвер уже вернул дефолт, если аватара нет).

**Кэш обязателен.** Топ-бар рендерится на каждой странице → наивное чтение из БД в advice = +1 запрос к БД на **каждый** HTTP-запрос (включая HTMX). Недопустимо:
- `@EnableCaching` + `ConcurrentMapCacheManager` (in-memory достаточно — один инстанс, см. `architecture_v1.md` §2).
- Кэшировать **чтение `avatar_url` из БД** по `userId` (`@Cacheable("userAvatars")`) в сервисе; резолвинг применять к результату (резолвер дешёвый, кэшировать его не нужно).
- **`@CacheEvict("userAvatars", key=userId)` во ВСЕХ точках записи `avatar_url`:** выбор пресета (шаг 3), загрузка файла [uploadAvatar](src/main/java/com/lep/portal/user/UserProfileController.java#L114) (#12), любой будущий путь. Иначе сменённый аватар «залипнет» до перезапуска.

### 7. Унификация дефолта (мелочь)
- Свести два дефолтных файла (`default-avatar.svg` и `avatar-default.png`) к одному; адрес дефолта — в конфиг (его тоже отдаёт резолвер).

## Узкие места

- 🔴 **ВСЕ точки показа аватара — через резолвер.** Раз в БД теперь лежит `preset:cat` (а не URL), любой шаблон, выводящий `avatar_url` напрямую в `src`, покажет битую строку `preset:cat`. Перевести на резолвер: топ-бар (advice), [user/profile.html](src/main/resources/templates/user/profile.html), [profile.html](src/main/resources/templates/profile.html), галерея edit. Это главный риск регрессии при смене модели.
- **`@ModelAttribute` на неаутентифицированных страницах** — обязательно null-safe (`principal == null` → не падать).
- **Оверхед БД** на каждый запрос — закрывается обязательным кэшем (шаг 6); риск кэша — «залипший» аватар, если забыть `@CacheEvict` хоть в одной точке записи. In-memory кэш — на один инстанс; при масштабировании пересмотреть.
- **Валидация `key` по whitelist** — без неё можно записать произвольное значение в `avatar_url`. Обязательно.
- **Синхронизация whitelist ↔ файлы**: ключ без файла → битая картинка. Опционально — лог-warning при старте, если файла нет.
- **Связь с #12/#38:** preset-выбор и upload — две точки записи в `avatar_url` (обе должны делать `@CacheEvict`); при #38 обе + резолвер переедут в `AvatarService`.
- **Хрупкость хранения — РЕШЕНА** этой моделью: ключ в БД + базовый адрес в env. Переезд статики/CDN/смена префикса URL = правка env, без миграции данных. (Загруженные R2-фото по-прежнему хранят готовый URL — их переезд потребовал бы миграции; унификация R2 в схему `r2:{key}` — опционально в #38.)

## Автотесты

Добавить в существующий [UserProfileIntegrationTest](src/test/java/com/lep/portal/UserProfileIntegrationTest.java) (там уже есть setup с сессией и seed-данными):

| # | Тест | Что проверяет |
|---|---|---|
| T-P1 | `POST /profile/avatar/preset` с валидным `key` → 3xx redirect + в БД `preset:{key}` | базовый happy path |
| T-P2 | `POST /profile/avatar/preset` с `key` вне whitelist → не redirect (400 или форма) + `avatar_url` не изменился | валидация whitelist |
| T-P3 | После выбора пресета `GET /profile/edit` рендерится без ошибок (нет строки `preset:` в `src`) | резолвер работает в шаблоне |
| T-P4 | `@ControllerAdvice` advice: для аутентифицированного пользователя с `preset:cat` в модели есть `currentAvatarUrl`, не содержащий `preset:` | advice вызывает резолвер |
| T-P5 | `GET /login` (неаутентифицированный) — 200, нет NPE | advice null-safe |

> Проверку п.4 чек-листа («переезд через env») — **не автотестировать**: это интеграционная проверка конфига, делается вручную один раз. Достаточно unit-теста на `AvatarUrlResolver`: при `preset:cat` + `baseUrl="/x/"` → результат `/x/cat.png`; при готовом URL → тот же URL; при `null` → дефолт.

После реализации обновить счётчики тестов в `docs/AI_IMPLEMENTATION_GUIDE.md` (как принято в проекте).

- `FriendActivityDTO` и блок «Что делают другие» (онлайн-виджет — отдельная задача).
- Алгоритм загрузки в R2 (#7/#12) — только добавляем `@CacheEvict` в `uploadAvatar`.
- Логику смены имени/пароля/email.

## Как проверить

1. В профиле есть галерея готовых аватаров; клик по пресету → в БД сохраняется `preset:{key}`, текущий выделен (сравнение по ключу).
2. Выбранный аватар отображается в профиле и топ-баре (на всех страницах) — через резолвер, без строки `preset:...` в `src`.
3. У пользователя без аватара (`null`) везде показывается единый дефолт.
4. **Переезд (ключевая проверка модели):** изменить `AVATAR_PRESET_BASE_URL` (например на CDN-домен) → у всех пресет-аватаров адрес обновляется **без изменения данных в БД**.
5. Загруженное R2-фото (#12) продолжает отображаться (резолвер отдаёт его URL как есть).
6. `POST /profile/avatar/preset` с `key` вне whitelist → отклонён.
7. Смена аватара сразу отражается в топ-баре (кэш инвалидируется через `@CacheEvict`).
8. Неаутентифицированные страницы (login/register) открываются без ошибок (advice null-safe).
