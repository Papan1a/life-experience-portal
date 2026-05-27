# Issue #8 — "Мой опыт": показывается код вместо названия активности/варианта

## Контекст

**Причина бага:** в `ExperiencePageController.java` карта titles строится с ключом `UUID.toString()` (строка), а в шаблоне `my_experiences.html` доступ к карте идёт по `ux.activityId` (UUID-объект). Java `HashMap.get(UUID)` не найдёт строковый ключ → возвращает null → шаблон показывает `'Активность #<UUID>'`.

## Файлы

- `src/main/java/com/lep/portal/experience/ExperiencePageController.java`

## Что сделать

В методе `myExperiences()` изменить тип ключей в map с `String` на `UUID`:

```java
Map<UUID, String> activityTitles = new HashMap<>();
Map<UUID, String> variantTitles = new HashMap<>();

// ...

activityTitles.put(ux.getActivityId(),
        act.map(Activity::getTitle).orElse("Активность #" + ux.getActivityId()));

// ...

variantTitles.put(ux.getVariantId(),
        var.map(Variant::getTitle).orElse("Вариант"));
```

Шаблон `my_experiences.html` **менять не нужно** — `activityTitles[ux.activityId]` после исправления сработает корректно, так как ключ и значение будут одного типа UUID.

## Проверка

1. Зайти на `/my-experiences`
2. Отмеченные активности показывают **название** ("Футбол"), не UUID
3. Если есть вариант — показывает **название варианта** ("Пляжный футбол")
