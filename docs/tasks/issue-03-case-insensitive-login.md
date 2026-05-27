# Issue #3 — Логин по email регистронезависимый

## Контекст

В БД колонка `users.email` имеет тип `citext` (PostgreSQL) — сравнение уже регистронезависимо на уровне БД. Тем не менее хорошей практикой является нормализация email к lowercase **до сохранения и до поиска** — это гарантирует корректность независимо от JDBC-настроек.

## Файлы

- `src/main/java/com/lep/portal/user/UserService.java`
- `src/main/java/com/lep/portal/user/PortalUserDetailsService.java`

## Что сделать

### 1. `UserService.java` — нормализовать email при регистрации

В методе `register(form)` перед сохранением пользователя:

```java
user.setEmail(form.getEmail().trim().toLowerCase());
```

### 2. `PortalUserDetailsService.java` — нормализовать email при логине

```java
@Override
public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    String normalizedEmail = email.trim().toLowerCase();
    User user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + normalizedEmail));
    return new PortalUserDetails(user);
}
```

### 3. Проверка уникальности при регистрации

Убедиться что при проверке дубля email в `UserService.register` тоже используется нормализованный email.

## Проверка

1. Зарегистрироваться с email `Test@Example.com`
2. Войти с `test@example.com` → успешный вход
3. Войти с `TEST@EXAMPLE.COM` → успешный вход
4. Попробовать зарегистрировать ещё одного с `test@example.com` → ошибка "email уже занят"
