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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
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
public class ContentCreationIntegrationTest {

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

    private User creatorUser;
    private User otherUser;
    private jakarta.servlet.http.Cookie creatorSession;
    private jakarta.servlet.http.Cookie otherSession;
    private UUID sportCategoryId;
    private UUID existingActivityId;

    @BeforeEach
    void setUp() {
        // Clean all data
        jdbcTemplate.update("DELETE FROM bookmarks");
        jdbcTemplate.update("DELETE FROM user_experiences");
        jdbcTemplate.update("DELETE FROM variant_tags");
        jdbcTemplate.update("DELETE FROM activity_tags");
        jdbcTemplate.update("DELETE FROM variants");
        jdbcTemplate.update("DELETE FROM activities");
        jdbcTemplate.update("DELETE FROM tags");
        jdbcTemplate.update("DELETE FROM invites");
        jdbcTemplate.update("DELETE FROM users");

        // Create two users
        creatorUser = new User();
        creatorUser.setEmail("creator@test.com");
        creatorUser.setPasswordHash(passwordEncoder.encode("password123"));
        creatorUser.setDisplayName("Creator User");
        creatorUser.setAdmin(false);
        creatorUser = userRepository.save(creatorUser);

        otherUser = new User();
        otherUser.setEmail("other@test.com");
        otherUser.setPasswordHash(passwordEncoder.encode("password123"));
        otherUser.setDisplayName("Other User");
        otherUser.setAdmin(false);
        otherUser = userRepository.save(otherUser);

        // Resolve category ID from seeded V1 data
        sportCategoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE slug = 'sport'", UUID.class);
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

    // ---- Helpers ----

    private jakarta.servlet.http.Cookie login(String email, String password) throws Exception {
        var result = mockMvc.perform(formLogin("/login")
                        .user("username", email)
                        .password(password))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        var cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private void loginCreator() throws Exception {
        creatorSession = login("creator@test.com", "password123");
    }

    private void loginOther() throws Exception {
        otherSession = login("other@test.com", "password123");
    }

    /** Helper: create an activity via POST and return the redirect location ID. */
    private UUID createActivityViaApi(String title, jakarta.servlet.http.Cookie session) throws Exception {
        String redirect = mockMvc.perform(post("/activities")
                        .cookie(session)
                        .with(csrf())
                        .param("title", title)
                        .param("categoryId", sportCategoryId.toString())
                        .param("complexity", "medium")
                        .param("costTier", "free"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/activities/*"))
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
        return UUID.fromString(redirect.substring(redirect.lastIndexOf('/') + 1));
    }

    // ---- T4.1 — GET /activities/new (авторизован) → 200 ----

    @Test
    @Order(1)
    @DisplayName("T4.1 — GET /activities/new (авторизован) → 200")
    void createFormReturns200() throws Exception {
        loginCreator();

        mockMvc.perform(get("/activities/new").cookie(creatorSession))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Новая активность")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Название")));
    }

    // ---- T4.2 — POST /activities с валидными данными → Activity создана, redirect ----

    @Test
    @Order(2)
    @DisplayName("T4.2 — POST /activities с валидными данными → Activity создана, redirect")
    void createValidActivityRedirects() throws Exception {
        loginCreator();

        mockMvc.perform(post("/activities")
                        .cookie(creatorSession)
                        .with(csrf())
                        .param("title", "Горные лыжи")
                        .param("description", "Катание на горных лыжах")
                        .param("categoryId", sportCategoryId.toString())
                        .param("complexity", "advanced")
                        .param("costTier", "high")
                        .param("estimatedDuration", "4 часа")
                        .param("tags", "горы, зима"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/activities/*"));

        // Verify the activity was created in DB
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activities WHERE title = 'Горные лыжи' AND status = 'ACTIVE'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // ---- T4.3 — POST /activities без title → ошибка валидации на форме ----

    @Test
    @Order(3)
    @DisplayName("T4.3 — POST /activities без title → ошибка валидации на форме")
    void createWithoutTitleShowsValidationError() throws Exception {
        loginCreator();

        // Submit without title — Spring MVC @Valid should add binding error
        mockMvc.perform(post("/activities")
                        .cookie(creatorSession)
                        .with(csrf())
                        .param("title", "")
                        .param("categoryId", sportCategoryId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Новая активность")));
    }

    // ---- T4.4 — POST /activities с title существующей активити → форма с предупреждением о дубле ----

    @Test
    @Order(4)
    @DisplayName("T4.4 — POST /activities с title существующей активити → предупреждение о дубле")
    void duplicateTitleShowsWarning() throws Exception {
        loginCreator();

        // First, create an activity
        existingActivityId = createActivityViaApi("Сноуборд", creatorSession);

        // Now try to create another with the same title (case-insensitive)
        mockMvc.perform(post("/activities")
                        .cookie(creatorSession)
                        .with(csrf())
                        .param("title", "Сноуборд")
                        .param("categoryId", sportCategoryId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Похожая активность уже существует")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Сноуборд")));
    }

    // ---- T4.5 — POST /activities с title дубля + force=true → Activity создана ----

    @Test
    @Order(5)
    @DisplayName("T4.5 — POST /activities с title дубля + force=true → Activity создана")
    void forceCreateDuplicateTitleSucceeds() throws Exception {
        loginCreator();

        // Create first activity
        createActivityViaApi("Кайтсерфинг", creatorSession);

        // Force-create a second with the same title
        mockMvc.perform(post("/activities")
                        .cookie(creatorSession)
                        .with(csrf())
                        .param("title", "Кайтсерфинг")
                        .param("categoryId", sportCategoryId.toString())
                        .param("force", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/activities/*"));

        // Both should exist
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activities WHERE lower(title) = 'кайтсерфинг' AND status = 'ACTIVE'",
                Integer.class);
        assertThat(count).isEqualTo(2);
    }

    // ---- T4.6 — GET /tags/suggest?q=wa (HTMX) → список тегов начинающихся с "wa" ----

    @Test
    @Order(6)
    @DisplayName("T4.6 — GET /tags/suggest?q=wa (HTMX) → список тегов с префиксом wa")
    void tagSuggestByPrefix() throws Exception {
        loginCreator();

        // Pre-create tags
        jdbcTemplate.update(
                "INSERT INTO tags (id, slug, name, created_by) VALUES (?::uuid, 'water', 'Вода', ?::uuid)",
                UUID.randomUUID().toString(), creatorUser.getId().toString());
        jdbcTemplate.update(
                "INSERT INTO tags (id, slug, name, created_by) VALUES (?::uuid, 'walking', 'Ходьба', ?::uuid)",
                UUID.randomUUID().toString(), creatorUser.getId().toString());
        jdbcTemplate.update(
                "INSERT INTO tags (id, slug, name, created_by) VALUES (?::uuid, 'wakeboard', 'Вейкбординг', ?::uuid)",
                UUID.randomUUID().toString(), creatorUser.getId().toString());
        jdbcTemplate.update(
                "INSERT INTO tags (id, slug, name, created_by) VALUES (?::uuid, 'cycling', 'Велоспорт', ?::uuid)",
                UUID.randomUUID().toString(), creatorUser.getId().toString());

        mockMvc.perform(get("/tags/suggest")
                        .cookie(creatorSession)
                        .param("q", "wa"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Вода")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Ходьба")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Виндсёрфинг")))
                // "cycling" should NOT be present
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("Велоспорт"))));
    }

    // ---- T4.7 — POST /activities/{activityId}/variants → Variant создан, status=ACTIVE ----

    @Test
    @Order(7)
    @DisplayName("T4.7 — POST /activities/{activityId}/variants → Variant создан, status=ACTIVE")
    void createVariantSucceeds() throws Exception {
        loginCreator();

        UUID activityId = createActivityViaApi("Плавание", creatorSession);

        mockMvc.perform(post("/activities/" + activityId + "/variants")
                        .cookie(creatorSession)
                        .with(csrf())
                        .param("title", "Плавание в открытой воде")
                        .param("description", "Плавание в озере или море")
                        .param("differenceReason", "Открытая вода вместо бассейна")
                        .param("tags", "вода, экстрим"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/activities/" + activityId + "/variants/*"));

        // Verify variant exists and is ACTIVE
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM variants WHERE activity_id = ?::uuid AND status = 'ACTIVE'",
                Integer.class, activityId.toString());
        assertThat(count).isEqualTo(1);
    }

    // ---- T4.8 — GET /activities/{id}/edit чужой активити → 403 ----

    @Test
    @Order(8)
    @DisplayName("T4.8 — GET /activities/{id}/edit чужой активити (не автор, не admin) → 403")
    void editOtherUsersActivityReturns403() throws Exception {
        loginCreator();
        UUID activityId = createActivityViaApi("Дайвинг", creatorSession);

        // Login as other user and try to edit creator's activity
        loginOther();

        mockMvc.perform(get("/activities/" + activityId + "/edit").cookie(otherSession))
                .andExpect(status().isForbidden());
    }
}
