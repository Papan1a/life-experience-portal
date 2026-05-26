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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.lep.portal.user.User;
import com.lep.portal.user.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExperienceIntegrationTest {

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
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private User seedUser;
    private jakarta.servlet.http.Cookie sessionCookie;
    private UUID sportCategoryId;
    private UUID sportActivityId;
    private UUID waterVariantId;

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
        seedUser.setEmail("exp@test.com");
        seedUser.setPasswordHash(passwordEncoder.encode("password123"));
        seedUser.setDisplayName("Exp Tester");
        seedUser.setAdmin(false);
        seedUser = userRepository.save(seedUser);

        // Resolve category
        sportCategoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE slug = 'sport'", UUID.class);

        // Create a visible activity
        sportActivityId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO activities (id, title, description, category_id, complexity, cost_tier, " +
                "estimated_duration, created_by, status) " +
                "VALUES (?::uuid, 'Футбол', 'Командная игра', ?::uuid, 'medium', 'low', " +
                "'90 мин', ?::uuid, 'ACTIVE')",
                sportActivityId.toString(), sportCategoryId.toString(), seedUser.getId().toString());

        // Create a visible variant
        waterVariantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO variants (id, activity_id, title, description, created_by, status) " +
                "VALUES (?::uuid, ?::uuid, 'Пляжный футбол', 'На песке', ?::uuid, 'ACTIVE')",
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
                        .user("username", "exp@test.com")
                        .password("password123"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        sessionCookie = result.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();
    }

    // ---- T5.1 — POST /experiences (WANT_TO_TRY) → строка создана, фрагмент возвращён ----

    @Test
    @Order(1)
    @DisplayName("T5.1 — POST /experiences (WANT_TO_TRY) → строка создана, фрагмент возвращён")
    void setStatusWantToTry() throws Exception {
        login();

        mockMvc.perform(post("/experiences")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .param("activityId", sportActivityId.toString())
                        .param("variantId", "")
                        .param("status", "WANT_TO_TRY"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("status-buttons")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("btn-primary")));

        // Assert row persisted
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_experiences WHERE user_id = ?::uuid AND activity_id = ?::uuid AND variant_id IS NULL",
                Integer.class, seedUser.getId().toString(), sportActivityId.toString());
        assertThat(count).isEqualTo(1);
    }

    // ---- T5.2 — POST /experiences повторно с другим статусом → строка обновлена ----

    @Test
    @Order(2)
    @DisplayName("T5.2 — POST /experiences повторно с другим статусом → строка обновлена")
    void setStatusUpdateExisting() throws Exception {
        login();

        // Insert initial status
        UUID uxId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO user_experiences (id, user_id, activity_id, variant_id, status) " +
                "VALUES (?::uuid, ?::uuid, ?::uuid, NULL, 'INTERESTING')",
                uxId.toString(), seedUser.getId().toString(), sportActivityId.toString());

        // Change to TRIED
        mockMvc.perform(post("/experiences")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .param("activityId", sportActivityId.toString())
                        .param("variantId", "")
                        .param("status", "TRIED"))
                .andExpect(status().isOk());

        // Assert exactly 1 row, updated status
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_experiences WHERE user_id = ?::uuid AND activity_id = ?::uuid AND variant_id IS NULL",
                Integer.class, seedUser.getId().toString(), sportActivityId.toString());
        assertThat(count).isEqualTo(1);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM user_experiences WHERE user_id = ?::uuid AND activity_id = ?::uuid AND variant_id IS NULL",
                String.class, seedUser.getId().toString(), sportActivityId.toString());
        assertThat(status).isEqualTo("TRIED");
    }

    // ---- T5.3 — POST /experiences с variant_id → отдельная строка от activity-level ----

    @Test
    @Order(3)
    @DisplayName("T5.3 — POST /experiences с variant_id → отдельная строка от activity-level")
    void setStatusWithVariantSeparateRow() throws Exception {
        login();

        // Set status on activity level (no variant)
        mockMvc.perform(post("/experiences")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .param("activityId", sportActivityId.toString())
                        .param("variantId", "")
                        .param("status", "INTERESTING"))
                .andExpect(status().isOk());

        // Set status on variant level
        mockMvc.perform(post("/experiences")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .param("activityId", sportActivityId.toString())
                        .param("variantId", waterVariantId.toString())
                        .param("status", "WANT_TO_TRY"))
                .andExpect(status().isOk());

        // Both rows should exist independently
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_experiences WHERE user_id = ?::uuid AND activity_id = ?::uuid",
                Integer.class, seedUser.getId().toString(), sportActivityId.toString());
        assertThat(count).isEqualTo(2);
    }

    // ---- T5.4 — POST /experiences status=null → строка удалена ----

    @Test
    @Order(4)
    @DisplayName("T5.4 — POST /experiences status=null → строка удалена")
    void setStatusClearRemovesRow() throws Exception {
        login();

        // Insert initial status
        UUID uxId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO user_experiences (id, user_id, activity_id, variant_id, status) " +
                "VALUES (?::uuid, ?::uuid, ?::uuid, NULL, 'INTERESTING')",
                uxId.toString(), seedUser.getId().toString(), sportActivityId.toString());

        // Clear by sending empty status
        mockMvc.perform(post("/experiences")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .param("activityId", sportActivityId.toString())
                        .param("variantId", "")
                        .param("status", ""))
                .andExpect(status().isOk());

        // Row should be deleted
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_experiences WHERE user_id = ?::uuid AND activity_id = ?::uuid AND variant_id IS NULL",
                Integer.class, seedUser.getId().toString(), sportActivityId.toString());
        assertThat(count).isEqualTo(0);
    }

    // ---- T5.5 — POST /bookmarks/toggle → закладка создана; повторно → удалена ----

    @Test
    @Order(5)
    @DisplayName("T5.5 — POST /bookmarks/toggle → закладка создана; повторно → удалена")
    void toggleBookmark() throws Exception {
        login();

        // First toggle: create
        mockMvc.perform(post("/bookmarks/toggle")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .param("activityId", sportActivityId.toString())
                        .param("variantId", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("bookmark-button")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Сохранено")));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bookmarks WHERE user_id = ?::uuid AND activity_id = ?::uuid AND variant_id IS NULL",
                Integer.class, seedUser.getId().toString(), sportActivityId.toString());
        assertThat(count).isEqualTo(1);

        // Second toggle: delete
        mockMvc.perform(post("/bookmarks/toggle")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .param("activityId", sportActivityId.toString())
                        .param("variantId", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Сохранить")));

        count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bookmarks WHERE user_id = ?::uuid AND activity_id = ?::uuid AND variant_id IS NULL",
                Integer.class, seedUser.getId().toString(), sportActivityId.toString());
        assertThat(count).isEqualTo(0);
    }

    // ---- T5.6 — GET /saved → 200, список закладок текущего пользователя ----

    @Test
    @Order(6)
    @DisplayName("T5.6 — GET /saved → 200, список закладок текущего пользователя")
    void savedPage() throws Exception {
        login();

        // Create a bookmark
        UUID bmId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO bookmarks (id, user_id, activity_id, variant_id) " +
                "VALUES (?::uuid, ?::uuid, ?::uuid, NULL)",
                bmId.toString(), seedUser.getId().toString(), sportActivityId.toString());

        mockMvc.perform(get("/saved").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Сохранённое")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Футбол")));
    }

    // ---- T5.7 — GET /my-experiences → 200, сгруппировано по статусу ----

    @Test
    @Order(7)
    @DisplayName("T5.7 — GET /my-experiences → 200, сгруппировано по статусу")
    void myExperiencesPage() throws Exception {
        login();

        // Create user experiences with different statuses
        UUID uxId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO user_experiences (id, user_id, activity_id, variant_id, status) " +
                "VALUES (?::uuid, ?::uuid, ?::uuid, NULL, 'WANT_TO_TRY')",
                uxId.toString(), seedUser.getId().toString(), sportActivityId.toString());

        mockMvc.perform(get("/my-experiences").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Мой опыт")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Хочу попробовать")));
    }

    // ---- T5.8 — GET /saved другого пользователя → недоступно (только свои закладки) ----

    @Test
    @Order(8)
    @DisplayName("T5.8 — GET /saved отдаёт только закладки текущего пользователя")
    void savedOnlyShowsOwnBookmarks() throws Exception {
        // Create another user with a bookmark
        User other = new User();
        other.setEmail("other@test.com");
        other.setPasswordHash(passwordEncoder.encode("password123"));
        other.setDisplayName("Other");
        other.setAdmin(false);
        other = userRepository.save(other);

        UUID otherActivityId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO activities (id, title, description, category_id, created_by, status) " +
                "VALUES (?::uuid, 'Чужое хобби', 'Описание', ?::uuid, ?::uuid, 'ACTIVE')",
                otherActivityId.toString(), sportCategoryId.toString(), other.getId().toString());

        UUID otherBmId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO bookmarks (id, user_id, activity_id, variant_id) " +
                "VALUES (?::uuid, ?::uuid, ?::uuid, NULL)",
                otherBmId.toString(), other.getId().toString(), otherActivityId.toString());

        // Login as seed user
        login();

        // Create seed user's own bookmark
        UUID myBmId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO bookmarks (id, user_id, activity_id, variant_id) " +
                "VALUES (?::uuid, ?::uuid, ?::uuid, NULL)",
                myBmId.toString(), seedUser.getId().toString(), sportActivityId.toString());

        mockMvc.perform(get("/saved").cookie(sessionCookie))
                .andExpect(status().isOk())
                // Should show own "Футбол"
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Футбол")))
                // Should NOT show other user's "Чужое хобби"
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("Чужое хобби"))));
    }
}
