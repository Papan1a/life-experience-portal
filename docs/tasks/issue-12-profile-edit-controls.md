# Issue #12 — Управление профилем: смена пароля и email

## Контекст

В `/profile/edit` уже есть: редактирование displayName, bio, загрузка аватара.
Нужно добавить: **смена пароля** и **смена email**.

> Email verification (подтверждение по письму) **не делаем** — нет email-инфраструктуры в MVP.

## Файлы

- `src/main/java/com/lep/portal/user/UserService.java`
- `src/main/java/com/lep/portal/user/UserProfileController.java`
- `src/main/resources/templates/user/edit.html`
- `src/test/java/com/lep/portal/user/UserServiceTest.java` _(новый)_

---

## 1. UserService — два новых метода

### `changePassword(userId, currentPassword, newPassword)`

```java
public void changePassword(UUID userId, String currentPassword, String newPassword) {
    User user = findActiveById(userId);
    if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
        throw new IllegalArgumentException("Неверный текущий пароль");
    }
    if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
        throw new IllegalArgumentException("Новый пароль должен отличаться от текущего");
    }
    if (newPassword.length() < 8) {
        throw new IllegalArgumentException("Новый пароль должен содержать минимум 8 символов");
    }
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(user);
}
```

### `changeEmail(userId, newEmail, currentPassword)`

```java
public void changeEmail(UUID userId, String newEmail, String currentPassword) {
    User user = findActiveById(userId);
    if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
        throw new IllegalArgumentException("Неверный текущий пароль");
    }
    String normalized = newEmail.trim().toLowerCase();
    if (userRepository.findByEmail(normalized)
            .filter(u -> !u.getId().equals(userId))
            .isPresent()) {
        throw new IllegalArgumentException("Этот email уже занят");
    }
    user.setEmail(normalized);
    userRepository.save(user);
}
```

### `resetPassword` — добавить Javadoc

Существующий метод `resetPassword(UUID userId, String newPassword)` на строке 92 не трогаем,
но добавляем Javadoc чтобы разграничить семантику:

```java
/**
 * Административный сброс пароля без проверки текущего.
 * Для пользовательской смены пароля — {@link #changePassword}.
 */
public void resetPassword(UUID userId, String newPassword) { ... }
```

---

## 2. UserProfileController — два новых обработчика

`SecurityConfig` не изменяется — новые эндпоинты попадают под `.anyRequest().authenticated()`.

```java
@PostMapping("/profile/password")
public String changePassword(@AuthenticationPrincipal PortalUserDetails principal,
                             @RequestParam String currentPassword,
                             @RequestParam String newPassword,
                             @RequestParam String confirmPassword,
                             HttpServletRequest request,
                             RedirectAttributes ra) {
    if (currentPassword.isBlank() || newPassword.isBlank() || confirmPassword.isBlank()) {
        ra.addFlashAttribute("passwordError", "Все поля обязательны");
        return "redirect:/profile/edit";
    }
    if (!newPassword.equals(confirmPassword)) {
        ra.addFlashAttribute("passwordError", "Пароли не совпадают");
        return "redirect:/profile/edit";
    }
    try {
        userService.changePassword(principal.getUserId(), currentPassword, newPassword);
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        ra.addFlashAttribute("success", "Пароль изменён. Войдите заново.");
        return "redirect:/login";
    } catch (IllegalArgumentException e) {
        ra.addFlashAttribute("passwordError", e.getMessage());
    }
    return "redirect:/profile/edit";
}

@PostMapping("/profile/email")
public String changeEmail(@AuthenticationPrincipal PortalUserDetails principal,
                          @RequestParam String newEmail,
                          @RequestParam String passwordConfirm,
                          RedirectAttributes ra) {
    if (newEmail.isBlank() || passwordConfirm.isBlank()) {
        ra.addFlashAttribute("emailError", "Все поля обязательны");
        return "redirect:/profile/edit";
    }
    try {
        userService.changeEmail(principal.getUserId(), newEmail, passwordConfirm);
        ra.addFlashAttribute("success", "Email изменён");
    } catch (IllegalArgumentException e) {
        ra.addFlashAttribute("emailError", e.getMessage());
    }
    return "redirect:/profile/edit";
}
```

Импорты для контроллера:

```java
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
```

---

## 3. edit.html — две новые секции

Добавить после существующей карточки аватара:

### Секция смены пароля

```html
<div class="card mt-4">
    <h2 style="font-size: 1.1rem; margin-bottom: 16px;">Изменить пароль</h2>
    <div th:if="${passwordError}" class="alert alert-error mb-3" th:text="${passwordError}"></div>
    <form th:action="@{/profile/password}" method="post">
        <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}">
        <div class="form-group">
            <label>Текущий пароль</label>
            <input type="password" name="currentPassword" class="form-control" required>
        </div>
        <div class="form-group mt-3">
            <label>Новый пароль (мин. 8 символов)</label>
            <input type="password" name="newPassword" class="form-control" required minlength="8">
        </div>
        <div class="form-group mt-3">
            <label>Подтвердить новый пароль</label>
            <input type="password" name="confirmPassword" class="form-control" required minlength="8">
        </div>
        <button type="submit" class="btn btn-primary mt-4">Изменить пароль</button>
    </form>
</div>
```

### Секция смены email

```html
<div class="card mt-4">
    <h2 style="font-size: 1.1rem; margin-bottom: 16px;">Изменить email</h2>
    <p class="text-muted" style="font-size: 0.85rem; margin-bottom: 12px;">
        Текущий: <strong th:text="${user.email}">email@example.com</strong>
    </p>
    <div th:if="${emailError}" class="alert alert-error mb-3" th:text="${emailError}"></div>
    <form th:action="@{/profile/email}" method="post">
        <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}">
        <div class="form-group">
            <label>Новый email</label>
            <input type="email" name="newEmail" class="form-control" required>
        </div>
        <div class="form-group mt-3">
            <label>Текущий пароль (для подтверждения)</label>
            <input type="password" name="passwordConfirm" class="form-control" required>
        </div>
        <button type="submit" class="btn btn-primary mt-4">Изменить email</button>
    </form>
</div>
```

---

## 4. Тесты — UserServiceTest

Файл: `src/test/java/com/lep/portal/user/UserServiceTest.java`

Сценарии:

| # | Метод | Входные данные | Ожидаемый результат |
|---|-------|---------------|---------------------|
| 1 | `changePassword` | верный текущий пароль, новый ≥ 8 символов, отличается | пароль сохранён, `matches(newPassword, hash)` = true |
| 2 | `changePassword` | неверный текущий пароль | `IllegalArgumentException("Неверный текущий пароль")` |
| 3 | `changePassword` | новый пароль = текущему | `IllegalArgumentException("Новый пароль должен отличаться от текущего")` |
| 4 | `changeEmail` | email занят другим пользователем | `IllegalArgumentException("Этот email уже занят")` |
| 5 | `changeEmail` | пользователь отправляет свой же email | смена проходит без ошибки |

---

## Проверка

1. `/profile/edit` → смена пароля → неверный текущий пароль → ошибка на форме
2. `/profile/edit` → смена пароля → правильный пароль, новые не совпадают → ошибка
3. `/profile/edit` → смена пароля → новый = текущему → ошибка
4. `/profile/edit` → смена пароля → всё верно → редирект на `/login`, войти со старым паролем нельзя
5. `/profile/edit` → смена email → занятый email → ошибка
6. `/profile/edit` → смена email → свой же email → успешно, без ошибки
7. `/profile/edit` → смена email → новый email → отображается в профиле
