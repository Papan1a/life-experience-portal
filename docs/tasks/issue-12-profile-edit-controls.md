# Issue #12 — Управление профилем: смена имени, пароля, email, фото

## Контекст

Сейчас в `/profile/edit` есть: редактирование displayName, bio, загрузка аватара.
Нужно добавить: **смена пароля** и **смена email**.

Фото уже реализовано. Смена ФИО — тоже есть. Фокус на двух новых формах.

> Email verification (подтверждение по письму) **не делаем** — нет email-инфраструктуры в MVP.

## Файлы

- `src/main/java/com/lep/portal/user/UserService.java`
- `src/main/java/com/lep/portal/user/UserProfileController.java`
- `src/main/resources/templates/user/edit.html`

---

## 1. UserService — два новых метода

### `changePassword(userId, currentPassword, newPassword)`

```java
public void changePassword(UUID userId, String currentPassword, String newPassword) {
    User user = findActiveById(userId);
    if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
        throw new IllegalArgumentException("Неверный текущий пароль");
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
    if (userRepository.findByEmail(normalized).isPresent()) {
        throw new IllegalArgumentException("Этот email уже занят");
    }
    user.setEmail(normalized);
    userRepository.save(user);
}
```

---

## 2. UserProfileController — два новых обработчика

```java
@PostMapping("/profile/password")
public String changePassword(@AuthenticationPrincipal PortalUserDetails principal,
                             @RequestParam String currentPassword,
                             @RequestParam String newPassword,
                             @RequestParam String confirmPassword,
                             RedirectAttributes ra) {
    if (!newPassword.equals(confirmPassword)) {
        ra.addFlashAttribute("passwordError", "Пароли не совпадают");
        return "redirect:/profile/edit";
    }
    try {
        userService.changePassword(principal.getUserId(), currentPassword, newPassword);
        ra.addFlashAttribute("success", "Пароль изменён");
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
    try {
        userService.changeEmail(principal.getUserId(), newEmail, passwordConfirm);
        ra.addFlashAttribute("success", "Email изменён");
    } catch (IllegalArgumentException e) {
        ra.addFlashAttribute("emailError", e.getMessage());
    }
    return "redirect:/profile/edit";
}
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

## Проверка

1. `/profile/edit` → смена пароля → неверный текущий пароль → ошибка на форме
2. `/profile/edit` → смена пароля → правильный пароль, новые не совпадают → ошибка
3. `/profile/edit` → смена пароля → всё верно → успешно, можно войти с новым паролем
4. `/profile/edit` → смена email → занятый email → ошибка
5. `/profile/edit` → смена email → всё верно → новый email отображается в профиле
