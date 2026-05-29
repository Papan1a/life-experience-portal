# Issue #38 — Вынести логику аватара из контроллера в сервис

## Что нужно

Инфраструктурная логика обработки аватара (валидация, ресайз, заливка в R2/S3, построение URL) сейчас живёт прямо в контроллере. Вынести её в сервис — контроллер должен только принимать запрос и маппить результат/ошибки.

## Контекст

[UserProfileController](src/main/java/com/lep/portal/user/UserProfileController.java) (~230 строк) держит:
- `@Value` R2-настройки: `r2.bucket`, `r2.public-base-url`, `app.avatar-max-bytes` ([:41-48](src/main/java/com/lep/portal/user/UserProfileController.java#L41));
- зависимость `S3Client` (может быть `null`, если R2 не настроен);
- метод [uploadAvatar:114-171](src/main/java/com/lep/portal/user/UserProfileController.java#L114) — валидация content-type/размера, ресайз, `s3Client.putObject`, сборка public URL, `userService.updateAvatar`;
- приватный [resizeAvatar:221-228](src/main/java/com/lep/portal/user/UserProfileController.java#L221) (Thumbnailator).

Это нарушает слоистость: бизнес/инфраструктурная логика в контроллере (см. архитектурный аудит). [architecture_v1.md §7](docs/architecture_v1.md) описывает работу с аватаром как отдельную заботу (S3 client, resize, ключ `avatars/{user_id}`).

## Подход

1. Создать **`AvatarService`** (в пакете `user/`), перенести в него:
   - `@Value`-настройки R2 и `S3Client` (по-прежнему `@Autowired(required=false)` — может быть `null`);
   - валидацию content-type/размера;
   - ресайз (Thumbnailator);
   - заливку в R2 и построение URL (с текущим fallback `/avatar/{userId}`);
   - вызов `userService.updateAvatar(...)`.
   Сигнатура например: `String updateAvatar(UUID userId, MultipartFile file)` → возвращает новый URL либо кидает доменное исключение с понятным сообщением.

2. **Контроллер** `uploadAvatar` ужать до: принять `MultipartFile`, вызвать `avatarService.updateAvatar(...)`, поймать исключения и положить flash-сообщения (success/error), редирект. Никакой работы с S3/Thumbnailator в контроллере.

3. **Случай `s3Client == null`** (R2 не настроен) — сервис сигнализирует об этом доменным исключением/Optional; контроллер маппит в текущее сообщение «Хранилище аватаров не настроено». Поведение для пользователя не меняется.

## Узкие места

- **Чистый рефактор без смены поведения** — то, что работало/не работало раньше (включая зависимость от R2), должно остаться как есть. Реальное «доведение фото до рабочего состояния» — это #12/#7, не #38.
- **`@Value`-проперти** переносятся в сервис вместе со значениями по умолчанию (`app.avatar-max-bytes:2097152` и т.д.).
- **`S3Client` опционален** — сохранить `required=false`, не уронить контекст при отсутствии R2.
- **Тесты** ([UserProfileIntegrationTest](src/test/java/com/lep/portal/UserProfileIntegrationTest.java)) — прогнать; при наличии тестов на загрузку аватара убедиться, что они зелёные после переноса.
- Связь с **#12** (управление профилем) и **#7/R2** — не делать здесь R2-инфраструктуру; #38 только перемещает существующий код.

## Что НЕ трогаем

- Алгоритм ресайза/валидации (переносим как есть).
- Настройку R2/бакета, контракт `users.avatar_url`.
- Остальные методы профиля (имя/пароль/email) — кроме, при желании, аналогичной чистки позже.

## Как проверить

1. Загрузка аватара работает ровно как раньше (при настроенном R2 — грузится; при `s3Client == null` — сообщение «не настроено»).
2. В `UserProfileController` больше нет прямой работы с `S3Client`/Thumbnailator — только вызов `AvatarService` и маппинг ошибок.
3. Валидация типа (JPEG/PNG) и размера (макс. 2 MB) работает.
4. Сборка и тесты зелёные.
