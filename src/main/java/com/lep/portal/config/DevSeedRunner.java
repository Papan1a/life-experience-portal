package com.lep.portal.config;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds demo data for local development when the {@code dev} profile is active.
 * <p>
 * Runs after {@link BootstrapApplicationRunner} ({@code @Order(2)}) and is
 * fully idempotent: every INSERT is guarded by an existence check so the seed
 * is applied at most once regardless of restarts.
 * <p>
 * Seed content:
 * <ul>
 *   <li>1 admin — admin@admin / admin</li>
 *   <li>2 regular users — 1@1 / 1 and 2@2 / 2</li>
 *   <li>1 invite with predictable code {@code test-invite-001} (expires +365d)</li>
 *   <li>5 activities across 4 categories, with 5 variants and 5 tags</li>
 * </ul>
 */
@Component
@Profile("dev")
@Order(2)
public class DevSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeedRunner.class);

    /** Sentinel: if this activity exists the seed is considered already applied. */
    private static final String SENTINEL_TITLE = "Утренняя пробежка в парке";

    private static final String INVITE_CODE = "test-invite-001";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public DevSeedRunner(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        // ---- idempotency guard ----
        Integer already = jdbc.queryForObject(
                "SELECT COUNT(*) FROM activities WHERE title = ? AND deleted_at IS NULL",
                Integer.class, SENTINEL_TITLE);
        if (already != null && already > 0) {
            log.info("Dev seed already applied — skipping.");
            return;
        }

        log.info("=== DevSeedRunner: seeding demo data ===");

        // ---- 1. Users ----
        UUID adminId = insertUser("admin@admin", "admin", "Admin Dev", true);
        UUID user1Id = insertUser("1@1", "1", "Анна Тестова", false);
        UUID user2Id = insertUser("2@2", "2", "Борис Тестов", false);

        log.info("Users seeded: admin={}, user1={}, user2={}", adminId, user1Id, user2Id);

        // ---- 2. Invite (predictable code, long TTL) ----
        Integer inviteExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM invites WHERE code = ?", Integer.class, INVITE_CODE);
        UUID inviteId;
        if (inviteExists == null || inviteExists == 0) {
            inviteId = jdbc.queryForObject(
                    "INSERT INTO invites (code, created_by, expires_at) VALUES (?, ?, ?) RETURNING id",
                    UUID.class,
                    INVITE_CODE,
                    adminId,
                    Timestamp.from(Instant.now().plus(365, ChronoUnit.DAYS)));
            log.info("Invite seeded: code={}, id={}", INVITE_CODE, inviteId);
        } else {
            inviteId = jdbc.queryForObject(
                    "SELECT id FROM invites WHERE code = ?", UUID.class, INVITE_CODE);
            log.info("Invite already exists: code={}, id={}", INVITE_CODE, inviteId);
        }

        // ---- 3. Category lookups ----
        UUID catSport = getCategoryBySlug("sport");
        UUID catHomeLife = getCategoryBySlug("home_life");
        UUID catCreativity = getCategoryBySlug("creativity");
        UUID catNature = getCategoryBySlug("nature");

        // ---- 4. Activities ----
        UUID act1Id = insertActivity("Утренняя пробежка в парке",
                "Бодрящая пробежка по утреннему парку — отличный способ начать день с энергией и позитивом.",
                catSport, "low", "free", "30-45 минут", "Кроссовки, спортивная форма", adminId);
        UUID act2Id = insertActivity("Кулинарный мастер-класс",
                "Научитесь готовить изысканные блюда под руководством опытного шеф-повара.",
                catHomeLife, "medium", "mid", "2-3 часа", "Фартук, хорошее настроение", adminId);
        UUID act3Id = insertActivity("Фотоохота в городе",
                "Прогулка по городу в поисках интересных кадров — архитектура, стрит-фото, детали.",
                catCreativity, "low", "free", "1-2 часа", "Фотоаппарат или смартфон с камерой", user1Id);
        UUID act4Id = insertActivity("Велопрогулка по набережной",
                "Неспешная поездка на велосипеде вдоль реки с остановками в живописных местах.",
                catSport, "low", "free", "1-1.5 часа", "Велосипед, шлем, бутылка воды", user2Id);
        UUID act5Id = insertActivity("Медитация на закате",
                "Расслабляющая медитация на берегу озера под звуки заходящего солнца.",
                catNature, "low", "free", "20-30 минут", "Коврик, тёплая одежда по погоде", adminId);

        log.info("Activities seeded: {} {} {} {} {}",
                act1Id, act2Id, act3Id, act4Id, act5Id);

        // ---- 5. Tags ----
        UUID tagNewbie = findOrCreateTag("для новичков", adminId);
        UUID tagOutdoor = findOrCreateTag("на свежем воздухе", adminId);
        UUID tagGroup = findOrCreateTag("групповое", adminId);
        UUID tagGear = findOrCreateTag("требует снаряжения", adminId);
        UUID tagRelax = findOrCreateTag("расслабляет", adminId);

        log.info("Tags seeded: {} {} {} {} {}",
                tagNewbie, tagOutdoor, tagGroup, tagGear, tagRelax);

        // ---- 6. Activity ↔ Tag links ----
        linkActivityTag(act1Id, tagNewbie);   // пробежка — для новичков
        linkActivityTag(act1Id, tagOutdoor);   // пробежка — на свежем воздухе
        linkActivityTag(act2Id, tagGroup);     // кулинарный — групповое
        linkActivityTag(act2Id, tagGear);      // кулинарный — требует снаряжения
        linkActivityTag(act3Id, tagOutdoor);   // фотоохота — на свежем воздухе
        linkActivityTag(act3Id, tagNewbie);    // фотоохота — для новичков
        linkActivityTag(act4Id, tagOutdoor);   // вело — на свежем воздухе
        linkActivityTag(act4Id, tagGear);      // вело — требует снаряжения
        linkActivityTag(act5Id, tagRelax);     // медитация — расслабляет
        linkActivityTag(act5Id, tagNewbie);    // медитация — для новичков

        // ---- 7. Variants ----
        UUID var1Id = insertVariant(act1Id, "Интервальная пробежка",
                "Чередование быстрого и медленного темпа для максимального жиросжигания.",
                "Выше интенсивность, подходит для среднего уровня подготовки.",
                null, adminId);
        UUID var2Id = insertVariant(act2Id, "Итальянская паста",
                "Учимся готовить домашнюю пасту с нуля и три классических соуса.",
                "Фокус на итальянской кухне.",
                "Скалка для теста (если нет машинки для пасты)", adminId);
        UUID var3Id = insertVariant(act3Id, "Ночная фотоохота",
                "Съёмка города при искусственном освещении — игра теней и неоновых огней.",
                "Ночная съёмка вместо дневной.",
                "Штатив (рекомендуется)", user1Id);
        UUID var4Id = insertVariant(act4Id, "Горный веломаршрут",
                "Более сложный маршрут по пересечённой местности с подъёмами и спусками.",
                "Сложнее и длиннее базового маршрута.",
                "Горный велосипед, защита", user2Id);
        UUID var5Id = insertVariant(act5Id, "Медитация с поющими чашами",
                "Медитация под звуки тибетских поющих чаш для глубокого расслабления.",
                "Добавлено звуковое сопровождение.",
                "Поющие чаши (можно взять напрокат у инструктора)", adminId);

        log.info("Variants seeded: {} {} {} {} {}",
                var1Id, var2Id, var3Id, var4Id, var5Id);

        // ---- 8. Variant ↔ Tag links ----
        linkVariantTag(var1Id, tagOutdoor);
        linkVariantTag(var2Id, tagGroup);
        linkVariantTag(var3Id, tagGear);
        linkVariantTag(var4Id, tagGear);
        linkVariantTag(var5Id, tagRelax);

        log.info("=== DevSeedRunner: seeding complete ===");
    }

    // ---------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------

    private UUID insertUser(String email, String plainPassword, String displayName, boolean isAdmin) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ? AND deleted_at IS NULL",
                Integer.class, email);
        if (exists != null && exists > 0) {
            return jdbc.queryForObject(
                    "SELECT id FROM users WHERE email = ? AND deleted_at IS NULL",
                    UUID.class, email);
        }
        String hash = passwordEncoder.encode(plainPassword);
        return jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, display_name, is_admin, consent_at) " +
                "VALUES (?, ?, ?, ?, NOW()) RETURNING id",
                UUID.class, email, hash, displayName, isAdmin);
    }

    private UUID getCategoryBySlug(String slug) {
        return jdbc.queryForObject(
                "SELECT id FROM categories WHERE slug = ?", UUID.class, slug);
    }

    private UUID insertActivity(String title, String description,
                                UUID categoryId, String complexity,
                                String costTier, String estimatedDuration,
                                String requirements, UUID createdBy) {
        return jdbc.queryForObject(
                "INSERT INTO activities (title, description, category_id, complexity, " +
                "cost_tier, estimated_duration, requirements, created_by) " +
                "VALUES (?, ?, ?::uuid, ?::complexity_level, ?::cost_tier, ?, ?, ?::uuid) " +
                "RETURNING id",
                UUID.class,
                title, description, categoryId, complexity,
                costTier, estimatedDuration, requirements, createdBy);
    }

    private UUID findOrCreateTag(String name, UUID createdBy) {
        String slug = name.toLowerCase().replace(" ", "-");
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tags WHERE slug = ?", Integer.class, slug);
        if (exists != null && exists > 0) {
            return jdbc.queryForObject(
                    "SELECT id FROM tags WHERE slug = ?", UUID.class, slug);
        }
        return jdbc.queryForObject(
                "INSERT INTO tags (slug, name, created_by) VALUES (?, ?, ?::uuid) RETURNING id",
                UUID.class, slug, name, createdBy);
    }

    private void linkActivityTag(UUID activityId, UUID tagId) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM activity_tags WHERE activity_id = ? AND tag_id = ?",
                Integer.class, activityId, tagId);
        if (exists == null || exists == 0) {
            jdbc.update("INSERT INTO activity_tags (activity_id, tag_id) VALUES (?::uuid, ?::uuid)",
                    activityId, tagId);
        }
    }

    private UUID insertVariant(UUID activityId, String title, String description,
                               String differenceReason, String extraRequirements,
                               UUID createdBy) {
        return jdbc.queryForObject(
                "INSERT INTO variants (activity_id, title, description, difference_reason, " +
                "extra_requirements, created_by) " +
                "VALUES (?::uuid, ?, ?, ?, ?, ?::uuid) RETURNING id",
                UUID.class,
                activityId, title, description, differenceReason, extraRequirements, createdBy);
    }

    private void linkVariantTag(UUID variantId, UUID tagId) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM variant_tags WHERE variant_id = ? AND tag_id = ?",
                Integer.class, variantId, tagId);
        if (exists == null || exists == 0) {
            jdbc.update("INSERT INTO variant_tags (variant_id, tag_id) VALUES (?::uuid, ?::uuid)",
                    variantId, tagId);
        }
    }
}
