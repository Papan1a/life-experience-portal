package com.lep.portal;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.lep.portal.invite.InviteRepository;
import com.lep.portal.user.User;
import com.lep.portal.user.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CatalogIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("leptest")
            .withUsername("leptest")
            .withPassword("leptest");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> postgres.getJdbcUrl() + "&stringtype=unspecified");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private InviteRepository inviteRepository;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private User seedUser;
    private jakarta.servlet.http.Cookie sessionCookie;

    // Seed data IDs
    private UUID sportCategoryId;
    private UUID learningCategoryId;
    private UUID sportActivityId;
    private UUID learningActivityId;
    private UUID archivedActivityId;
    private UUID waterVariantId;
    private UUID waterTagId;

    @BeforeEach
    void setUp() {
        // Clean slate
        jdbcTemplate.update("DELETE FROM bookmarks");
        jdbcTemplate.update("DELETE FROM user_experiences");
        jdbcTemplate.update("DELETE FROM variant_tags");
        jdbcTemplate.update("DELETE FROM activity_tags");
        jdbcTemplate.update("DELETE FROM variants");
        jdbcTemplate.update("DELETE FROM activities");
        jdbcTemplate.update("DELETE FROM tags");
        jdbcTemplate.update("DELETE FROM invites");
        jdbcTemplate.update("DELETE FROM users");

        // Create seed user
        seedUser = new User();
        seedUser.setEmail("catalog@test.com");
        seedUser.setPasswordHash(passwordEncoder.encode("password123"));
        seedUser.setDisplayName("Catalog Tester");
        seedUser.setAdmin(false);
        seedUser = userRepository.save(seedUser);

        // Resolve category IDs from seeded V1 data
        sportCategoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE slug = 'sport'", UUID.class);
        learningCategoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE slug = 'learning'", UUID.class);

        // Create a visible activity in "sport"
        sportActivityId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO activities (id, title, description, category_id, complexity, cost_tier, " +
                "estimated_duration, requirements, interesting_reason, created_by, status) " +
                "VALUES (?::uuid, 'Футбол', 'Командная игра с мячом', ?::uuid, 'medium', 'low', " +
                "'90 мин', 'Мяч, форма', 'Отличный способ поддерживать форму', ?::uuid, 'ACTIVE')",
                sportActivityId.toString(), sportCategoryId.toString(), seedUser.getId().toString());

        // Create another visible activity in "learning"
        learningActivityId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO activities (id, title, description, category_id, complexity, cost_tier, " +
                "estimated_duration, created_by, status) " +
                "VALUES (?::uuid, 'Испанский язык', 'Изучение испанского', ?::uuid, 'medium', 'low', " +
                "'30 мин/день', ?::uuid, 'ACTIVE')",
                learningActivityId.toString(), learningCategoryId.toString(), seedUser.getId().toString());

        // Create an archived activity (for T3.6)
        archivedActivityId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO activities (id, title, description, category_id, created_by, status) " +
                "VALUES (?::uuid, 'Архивная активность', 'Не должна быть видна', ?::uuid, ?::uuid, 'ARCHIVED')",
                archivedActivityId.toString(), sportCategoryId.toString(), seedUser.getId().toString());

        // Create a tag "water"
        waterTagId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tags (id, slug, name, created_by) VALUES (?::uuid, 'water', 'Вода', ?::uuid)",
                waterTagId.toString(), seedUser.getId().toString());

        // Tag the football activity with "water"
        jdbcTemplate.update(
                "INSERT INTO activity_tags (activity_id, tag_id) VALUES (?::uuid, ?::uuid)",
                sportActivityId.toString(), waterTagId.toString());

        // Create a visible variant for the football activity
        waterVariantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO variants (id, activity_id, title, description, difference_reason, " +
                "created_by, status) " +
                "VALUES (?::uuid, ?::uuid, 'Пляжный футбол', 'Футбол на песке', 'Игра на песке вместо травы', " +
                "?::uuid, 'ACTIVE')",
                waterVariantId.toString(), sportActivityId.toString(), seedUser.getId().toString());
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM bookmarks");
        jdbcTemplate.update("DELETE FROM user_experiences");
        jdbcTemplate.update("DELETE FROM variant_tags");
        jdbcTemplate.update("DELETE FROM activity_tags");
        jdbcTemplate.update("DELETE FROM variants");
        jdbcTemplate.update("DELETE FROM activities");
        jdbcTemplate.update("DELETE FROM tags");
        jdbcTemplate.update("DELETE FROM invites");
        jdbcTemplate.update("DELETE FROM users");
    }

    /** Helper: authenticate and capture session cookie. */
    private void login() throws Exception {
        var result = mockMvc.perform(formLogin("/login")
                        .user("username", "catalog@test.com")
                        .password("password123"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        sessionCookie = result.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();
    }

    // ---- T3.1 — GET / authenticated → 200, has activities and categories ----
    @Test
    @Order(1)
    @DisplayName("T3.1 — GET / (авторизован) → 200, содержит список активностей и категории")
    void catalogPageShowsActivitiesAndCategories() throws Exception {
        login();

        mockMvc.perform(get("/").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Футбол")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Спорт")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Обучение")));
    }

    // ---- T3.2 — GET /?category=sport → only sport activities ----
    @Test
    @Order(2)
    @DisplayName("T3.2 — GET /?category=sport → только активности категории Спорт")
    void catalogFilterByCategorySport() throws Exception {
        login();

        mockMvc.perform(get("/").cookie(sessionCookie)
                        .param("category", "sport"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Футбол")))
                // Learning activity should NOT be present
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("Испанский язык"))));
    }

    // ---- T3.3 — GET /?tags=water → activities with tag water ----
    @Test
    @Order(3)
    @DisplayName("T3.3 — GET /?tags=water → активности с тегом water")
    void catalogFilterByTagWater() throws Exception {
        login();

        mockMvc.perform(get("/").cookie(sessionCookie)
                        .param("tags", "water"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Футбол")))
                // Learning activity should NOT be present
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("Испанский язык"))));
    }

    // ---- T3.4 — GET /activities/{id} → 200, variants visible ----
    @Test
    @Order(4)
    @DisplayName("T3.4 — GET /activities/{id} → 200, варианты видны")
    void activityDetailShowsVariants() throws Exception {
        login();

        mockMvc.perform(get("/activities/" + sportActivityId).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Футбол")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Пляжный футбол")));
    }

    // ---- T3.5 — GET /activities/{nonexistent} → 404 ----
    @Test
    @Order(5)
    @DisplayName("T3.5 — GET /activities/{nonexistent} → 404")
    void activityDetailNonexistentReturns404() throws Exception {
        login();

        mockMvc.perform(get("/activities/" + UUID.randomUUID()).cookie(sessionCookie))
                .andExpect(status().isNotFound());
    }

    // ---- T3.6 — GET /activities/{archivedId} → 404 (not visible via view) ----
    @Test
    @Order(6)
    @DisplayName("T3.6 — GET /activities/{archivedId} → 404 (невидима через вьюху)")
    void archivedActivityReturns404() throws Exception {
        login();

        mockMvc.perform(get("/activities/" + archivedActivityId).cookie(sessionCookie))
                .andExpect(status().isNotFound());
    }

    // ---- T3.7 — GET /activities/{activityId}/variants/{variantId} → 200 ----
    @Test
    @Order(7)
    @DisplayName("T3.7 — GET /activities/{activityId}/variants/{variantId} → 200")
    void variantDetailPage() throws Exception {
        login();

        mockMvc.perform(get("/activities/" + sportActivityId + "/variants/" + waterVariantId)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Пляжный футбол")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Футбол")));
    }

    // ---- T3.8 — HTMX filter: HX-Request header → fragment, not full page ----
    @Test
    @Order(8)
    @DisplayName("T3.8 — HTMX: GET /?category=sport + HX-Request:true → фрагмент")
    void htmxFilterReturnsFragment() throws Exception {
        login();

        mockMvc.perform(get("/").cookie(sessionCookie)
                        .param("category", "sport")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                // Fragment should contain the activity but NOT the full layout
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Футбол")))
                // Full page has <html> tag; fragment must not
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("<html"))))
                // Fragment should contain the Thymeleaf fragment name
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("catalog-results")));
    }

    // ---- T3.9 — Variant belonging to activity X, requested under activity Y → 404 ----
    @Test
    @Order(9)
    @DisplayName("T3.9 — GET /activities/{otherId}/variants/{variantId} (variant чужой активити) → 404")
    void variantOfOtherActivityReturns404() throws Exception {
        login();

        // waterVariant belongs to sportActivity, not learningActivity
        mockMvc.perform(get("/activities/" + learningActivityId + "/variants/" + waterVariantId)
                        .cookie(sessionCookie))
                .andExpect(status().isNotFound());
    }

    // ---- T3.10 — BLOCKED activity → 404 (not in visible_activities view) ----
    @Test
    @Order(10)
    @DisplayName("T3.10 — GET /activities/{blockedId} → 404 (BLOCKED не видна через вьюху)")
    void blockedActivityReturns404() throws Exception {
        login();

        UUID blockedActivityId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO activities (id, title, description, category_id, created_by, status) " +
                "VALUES (?::uuid, 'Заблокированная активность', 'Не должна быть видна', ?::uuid, ?::uuid, 'BLOCKED')",
                blockedActivityId.toString(), sportCategoryId.toString(), seedUser.getId().toString());

        mockMvc.perform(get("/activities/" + blockedActivityId).cookie(sessionCookie))
                .andExpect(status().isNotFound());
    }

    // ---- T3.11 — Pagination: GET /?page=2 with >20 activities ----
    @Test
    @Order(11)
    @DisplayName("T3.11 — GET /?page=2 → вторая страница каталога с пагинацией")
    void catalogPaginationSecondPage() throws Exception {
        login();

        // Create 21 additional activities (total will be > 20 with seed activities)
        for (int i = 1; i <= 21; i++) {
            String num = String.format("%02d", i);
            jdbcTemplate.update(
                    "INSERT INTO activities (id, title, description, category_id, created_by, status) " +
                    "VALUES (?::uuid, ?, 'Описание ' || ?, ?::uuid, ?::uuid, 'ACTIVE')",
                    UUID.randomUUID().toString(),
                    "Пагинация " + num,
                    num,
                    sportCategoryId.toString(),
                    seedUser.getId().toString());
        }

        mockMvc.perform(get("/").cookie(sessionCookie).param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Страница ")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("<strong>2</strong>")));
    }
}
