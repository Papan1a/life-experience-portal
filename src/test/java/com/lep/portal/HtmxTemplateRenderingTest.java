package com.lep.portal;

import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.lep.portal.user.User;
import com.lep.portal.user.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
public class HtmxTemplateRenderingTest {

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

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private User seedUser;
    private jakarta.servlet.http.Cookie sessionCookie;
    private UUID activityId;
    private UUID variantId;

    /**
     * Регулярка ловит необработанные Thymeleaf-выражения вида
     * hx-post="@{...}", hx-get="@{...}" и т.д.
     */
    private static final Pattern UNPROCESSED_HTMX_URL =
            Pattern.compile("hx-(post|get|put|delete|patch)\\s*=\\s*\"@\\{");

    @BeforeEach
    void setUp() throws Exception {
        // Чистая БД
        jdbcTemplate.update("DELETE FROM bookmarks");
        jdbcTemplate.update("DELETE FROM user_experiences");
        jdbcTemplate.update("DELETE FROM variant_tags");
        jdbcTemplate.update("DELETE FROM activity_tags");
        jdbcTemplate.update("DELETE FROM variants");
        jdbcTemplate.update("DELETE FROM activities");
        jdbcTemplate.update("DELETE FROM tags");
        jdbcTemplate.update("DELETE FROM invites");
        jdbcTemplate.update("DELETE FROM users");

        // Пользователь
        seedUser = new User();
        seedUser.setEmail("htmx-test@local.dev");
        seedUser.setPasswordHash(passwordEncoder.encode("password123"));
        seedUser.setDisplayName("HTMX Tester");
        seedUser.setAdmin(false);
        seedUser = userRepository.save(seedUser);

        // Категория из V1
        UUID categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE slug = 'sport'", UUID.class);

        // Активность
        activityId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO activities (id, title, description, category_id, complexity, " +
                "cost_tier, created_by, status) " +
                "VALUES (?::uuid, 'Тестовая активность', 'Описание', ?::uuid, 'medium', 'low', ?::uuid, 'ACTIVE')",
                activityId.toString(), categoryId.toString(), seedUser.getId().toString());

        // Вариант
        variantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO variants (id, activity_id, title, description, created_by, status) " +
                "VALUES (?::uuid, ?::uuid, 'Тестовый вариант', 'Описание', ?::uuid, 'ACTIVE')",
                variantId.toString(), activityId.toString(), seedUser.getId().toString());

        // Логин
        var result = mockMvc.perform(formLogin("/login")
                        .user("username", "htmx-test@local.dev")
                        .password("password123"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        sessionCookie = result.getResponse().getCookie("SESSION");
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

    // ---- Helper ----

    /**
     * Проверяет что HTML не содержит необработанных Thymeleaf-выражений
     * в HTMX-атрибутах. Это ловит баг когда @{...} остаётся литералом.
     */
    private void assertNoUnprocessedHtmxUrls(String html, String pageLabel) {
        var matcher = UNPROCESSED_HTMX_URL.matcher(html);
        if (matcher.find()) {
            int start = Math.max(0, matcher.start() - 30);
            int end = Math.min(html.length(), matcher.end() + 80);
            String context = html.substring(start, end);
            org.junit.jupiter.api.Assertions.fail(
                "На странице '" + pageLabel + "' найдено необработанное " +
                "Thymeleaf-выражение в HTMX-атрибуте.\n" +
                "Контекст:\n...\n" + context + "\n...\n" +
                "Используй th:attr=\"hx-post=@{/foo}\" вместо hx-post=\"@{/foo}\"");
        }
    }

    // ---- T_HTMX.1 — страница активности ----

    @Test
    @DisplayName("HTMX.1 — GET /activities/{id} не содержит литералов @{} в hx-* атрибутах")
    void activityDetailPageRendersHtmxUrlsCorrectly() throws Exception {
        String body = mockMvc.perform(get("/activities/" + activityId).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertNoUnprocessedHtmxUrls(body, "/activities/" + activityId);

        // Дополнительно: позитивная проверка что нужные URL есть
        assertThat(body).contains("hx-post=\"/experiences\"");
        assertThat(body).contains("hx-post=\"/bookmarks/toggle\"");
        assertThat(body).contains("hx-post=\"/reports\"");
    }

    // ---- T_HTMX.2 — страница варианта ----

    @Test
    @DisplayName("HTMX.2 — GET /activities/{aid}/variants/{vid} не содержит литералов @{}")
    void variantDetailPageRendersHtmxUrlsCorrectly() throws Exception {
        String body = mockMvc.perform(get("/activities/" + activityId + "/variants/" + variantId)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertNoUnprocessedHtmxUrls(body, "/activities/{id}/variants/{vid}");
    }

    // ---- T_HTMX.3 — главная (каталог) ----

    @Test
    @DisplayName("HTMX.3 — GET / не содержит литералов @{} в hx-* атрибутах")
    void homePageRendersHtmxUrlsCorrectly() throws Exception {
        String body = mockMvc.perform(get("/").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertNoUnprocessedHtmxUrls(body, "/");
    }

    // ---- T_HTMX.4 — форма создания активности ----

    @Test
    @DisplayName("HTMX.4 — GET /activities/new не содержит литералов @{}")
    void activityFormPageRendersHtmxUrlsCorrectly() throws Exception {
        String body = mockMvc.perform(get("/activities/new").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertNoUnprocessedHtmxUrls(body, "/activities/new");
    }
}
