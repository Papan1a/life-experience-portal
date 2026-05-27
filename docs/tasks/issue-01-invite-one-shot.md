# Задача: Одноразовые инвайты + лимит 5 активных

**Issues:** #1 (одноразовость + статусы), #7 (лимит 5 активных)
**Статус:** Запланировано

---

## Контекст и цель

Объединяем два issues:
- **#1** — Одноразовое использование инвайта + 4 статуса (Активен, Использован, Истёк, Отозван)
- **#7** — Не больше 5 активных инвайтов от одного пользователя

Существующие данные в БД не трогаем (backfill не делаем).

---

## 1. Миграция БД V6

**Файл:** `src/main/resources/db/migration/V6__invite_one_shot.sql`

```sql
-- One-shot invites: track who used the invite and when.
-- Status is computed from columns:
--   USED      → used_at IS NOT NULL
--   REVOKED   → revoked_at IS NOT NULL (and not used)
--   EXPIRED   → expires_at < now() (and not used/revoked)
--   ACTIVE    → otherwise

ALTER TABLE invites
    ADD COLUMN used_at         timestamptz,
    ADD COLUMN used_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL;

-- Speeds up "count active invites by creator" check for the 5-invite limit
CREATE INDEX idx_invites_active_by_creator
    ON invites(created_by)
    WHERE used_at IS NULL
      AND revoked_at IS NULL;
```

Существующие инвайты получат `used_at = NULL` — остаются активными. Backfill не делаем.

---

## 2. Java enum InviteStatus

**Файл:** `src/main/java/com/lep/portal/invite/InviteStatus.java`

```java
package com.lep.portal.invite;

/**
 * Computed status of an Invite (not stored in DB).
 * Derived from used_at, revoked_at, expires_at.
 */
public enum InviteStatus {
    ACTIVE,
    USED,
    EXPIRED,
    REVOKED
}
```

Enum **не хранится** в БД — вычисляется из timestamp-полей. Никаких `JdbcConverter` не нужно.

---

## 3. Invite.java — добавить поля и computed status

**Файл:** `src/main/java/com/lep/portal/invite/Invite.java`

Добавить два поля (после `revokedAt`):

```java
@Column("used_at")
private Instant usedAt;

@Column("used_by_user_id")
private UUID usedByUserId;
```

С геттерами/сеттерами. Добавить методы:

```java
public boolean isUsed() {
    return usedAt != null;
}

/**
 * Computed status. Priority: REVOKED > USED > EXPIRED > ACTIVE.
 */
public InviteStatus getStatus() {
    if (isRevoked()) return InviteStatus.REVOKED;
    if (isUsed())    return InviteStatus.USED;
    if (isExpired()) return InviteStatus.EXPIRED;
    return InviteStatus.ACTIVE;
}
```

Обновить `isValid()`:

```java
public boolean isValid() {
    return !isRevoked() && !isExpired() && !isUsed();
}
```

---

## 4. InviteRepository — добавить методы

**Файл:** `src/main/java/com/lep/portal/invite/InviteRepository.java`

```java
// Count active invites for limit check
@Query("SELECT COUNT(*) FROM invites WHERE created_by = :userId " +
       "AND used_at IS NULL AND revoked_at IS NULL AND expires_at > now()")
int countActiveByCreator(@Param("userId") UUID userId);

// Atomic mark-as-used. Returns 1 if marked, 0 if already used/revoked/expired.
// CRITICAL: must be atomic to prevent race condition.
@Modifying
@Query("UPDATE invites SET used_at = now(), used_by_user_id = :userId " +
       "WHERE id = :inviteId " +
       "AND used_at IS NULL AND revoked_at IS NULL AND expires_at > now()")
int markUsed(@Param("inviteId") UUID inviteId, @Param("userId") UUID userId);
```

---

## 5. InviteService.java — обновить логику

**Файл:** `src/main/java/com/lep/portal/invite/InviteService.java`

```java
private static final int MAX_ACTIVE_INVITES_PER_USER = 5;

public Invite createInvite(UUID createdBy) {
    int active = inviteRepository.countActiveByCreator(createdBy);
    if (active >= MAX_ACTIVE_INVITES_PER_USER) {
        throw new IllegalStateException(
            "У вас уже " + MAX_ACTIVE_INVITES_PER_USER + " активных приглашений. " +
            "Дождитесь использования или отзовите ненужные.");
    }
    Invite invite = new Invite();
    invite.setCode(generateCode());
    invite.setCreatedBy(createdBy);
    invite.setExpiresAt(Instant.now().plus(ttlDays, ChronoUnit.DAYS));
    return inviteRepository.save(invite);
}

/**
 * Validates code for registration. Does NOT mark as used.
 */
public Invite validateForRegistration(String code) {
    Invite invite = inviteRepository.findByCode(code)
            .orElseThrow(() -> new InvalidInviteException("Неверный код приглашения"));
    if (invite.isRevoked()) throw new InvalidInviteException("Код приглашения отозван");
    if (invite.isExpired()) throw new InvalidInviteException("Срок действия кода приглашения истёк");
    if (invite.isUsed())    throw new InvalidInviteException("Этот код уже использован");
    return invite;
}

/**
 * Atomically marks an invite as used.
 * Returns true if successful, false if already used/revoked/expired (race condition).
 */
public boolean markUsed(UUID inviteId, UUID userId) {
    return inviteRepository.markUsed(inviteId, userId) == 1;
}

public int countActiveInvites(UUID userId) {
    return inviteRepository.countActiveByCreator(userId);
}
```

Удалить старый метод `validateAndUse`.

---

## 6. UserService.register — пометить инвайт атомарно

**Файл:** `src/main/java/com/lep/portal/user/UserService.java`

В существующей транзакции `register(form)`:
1. Вызвать `inviteService.validateForRegistration(code)` (как раньше)
2. Создать и сохранить пользователя
3. Сразу после сохранения:

```java
boolean marked = inviteService.markUsed(invite.getId(), savedUser.getId());
if (!marked) {
    // Race condition — invite used by someone else in parallel
    throw new InvalidInviteException("Этот код только что был использован другим пользователем");
}
```

Если `markUsed` вернул `false` — транзакция откатится, юзер не сохранится.

---

## 7. InviteController.java

**Файл:** `src/main/java/com/lep/portal/invite/InviteController.java`

В `listMyInvites` добавить:
```java
model.addAttribute("activeCount", inviteService.countActiveInvites(principal.getUserId()));
model.addAttribute("maxActive", 5);
```

В `generateInvite` добавить обработку лимита:
```java
@PostMapping("/generate")
public String generateInvite(@AuthenticationPrincipal PortalUserDetails principal,
                              RedirectAttributes ra) {
    try {
        Invite invite = inviteService.createInvite(principal.getUserId());
        return "redirect:/invites/" + invite.getId();
    } catch (IllegalStateException e) {
        ra.addFlashAttribute("error", e.getMessage());
        return "redirect:/invites";
    }
}
```

---

## 8. invite/list.html — UI обновления

**Файл:** `src/main/resources/templates/invite/list.html`

### Счётчик и блокировка кнопки

```html
<p class="text-muted" style="margin-bottom: 12px;">
    Активных приглашений: <strong th:text="${activeCount}">0</strong>
    / <span th:text="${maxActive}">5</span>
</p>
<form th:action="@{/invites/generate}" method="post" style="margin-bottom: 24px;">
    <button type="submit" class="btn btn-primary"
            th:disabled="${activeCount >= maxActive}">
        Создать приглашение
    </button>
    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}">
</form>
```

### Колонка "Статус" — 4 бейджа

```html
<td>
    <span th:switch="${invite.status.name()}">
        <span th:case="'ACTIVE'"  class="badge badge-active">Активен</span>
        <span th:case="'USED'"    class="badge badge-used">Использован</span>
        <span th:case="'EXPIRED'" class="badge badge-expired">Истёк</span>
        <span th:case="'REVOKED'" class="badge badge-revoked">Отозван</span>
    </span>
</td>
```

### Кнопки "Скопировать" и "Отозвать" — только для ACTIVE

```html
th:if="${invite.status.name() == 'ACTIVE'}"
```

---

## 9. main.css — новый бейдж

**Файл:** `src/main/resources/static/css/main.css`

```css
.badge-used {
    background: #e0e7ff;
    color: #3730a3;
}
```

---

## 10. Тесты

**Файл:** `src/test/java/com/lep/portal/AuthIntegrationTest.java`

### T2.12 — Повторное использование инвайта → ошибка

```java
@Test
@Order(13)
@DisplayName("T2.12 — Повторное использование инвайта → пользователь не создан")
void inviteCanBeUsedOnlyOnce() throws Exception {
    // First registration succeeds
    mockMvc.perform(post("/register")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("inviteCode", validInviteCode)
            .param("displayName", "First User")
            .param("email", "first@test.com")
            .param("password", "password123")
            .param("consent", "true")
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/"));

    // Second registration with same code fails
    mockMvc.perform(post("/register")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("inviteCode", validInviteCode)
            .param("displayName", "Second User")
            .param("email", "second@test.com")
            .param("password", "password123")
            .param("consent", "true")
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/register?code=" + validInviteCode))
        .andExpect(flash().attributeExists("error"));

    // Second user does NOT exist in DB
    Boolean exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) > 0 FROM users WHERE email = ?",
            Boolean.class, "second@test.com");
    assertThat(exists).isFalse();

    // Invite is marked as used
    Boolean used = jdbcTemplate.queryForObject(
            "SELECT used_at IS NOT NULL FROM invites WHERE code = ?",
            Boolean.class, validInviteCode);
    assertThat(used).isTrue();
}
```

### T2.13 — Лимит 5 активных инвайтов

```java
@Test
@Order(14)
@DisplayName("T2.13 — Лимит 5 активных инвайтов → 6-й создать нельзя")
void cannotCreateMoreThan5ActiveInvites() throws Exception {
    var loginResult = mockMvc.perform(formLogin("/login")
                    .user("username", "seed@test.com")
                    .password("password123"))
            .andReturn();
    var cookie = loginResult.getResponse().getCookie("SESSION");

    // setUp creates 1 valid invite. Generate 4 more = 5 active total.
    for (int i = 0; i < 4; i++) {
        mockMvc.perform(post("/invites/generate").cookie(cookie).with(csrf()))
            .andExpect(status().is3xxRedirection());
    }

    // 6th attempt → redirect to /invites with error
    mockMvc.perform(post("/invites/generate").cookie(cookie).with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/invites"))
        .andExpect(flash().attributeExists("error"));

    Integer activeCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM invites WHERE created_by = ?::uuid " +
            "AND used_at IS NULL AND revoked_at IS NULL AND expires_at > now()",
            Integer.class, seedUser.getId().toString());
    assertThat(activeCount).isEqualTo(5);
}
```

---

## 11. Проверка после реализации

1. `docker compose up --build -d`
2. Войти admin → `/invites` → создать приглашение
3. Открыть инкогнито → зарегистрироваться по ссылке → успех
4. Войти admin → `/invites` → у приглашения статус **"Использован"**, кнопки "Скопировать"/"Отозвать" пропали
5. Открыть ту же ссылку в инкогнито → ошибка **"Этот код уже использован"**
6. Создать 5 приглашений → кнопка задизейблена, надпись `Активных: 5 / 5`
7. Отозвать одно → счётчик `4 / 5`, кнопка снова активна
8. `mvn test` — все тесты зелёные (77 существующих + 2 новых = 79)

---

## После реализации

- В `docs/ISSUES.md` зачеркнуть пункты **#1** и **#7**
- В `docs/BACKLOG.md` → "Выполнено" перенести задачу
