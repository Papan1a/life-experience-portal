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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.lep.portal.user.User;
import com.lep.portal.user.UserRepository;

import software.amazon.awssdk.services.s3.S3Client;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AdminIntegrationTest {

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

    @MockitoBean
    private S3Client s3Client;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User adminUser;
    private User regularUser;
    private jakarta.servlet.http.Cookie adminSession;
    private jakarta.servlet.http.Cookie regularSession;

    private UUID sportCategoryId;
    private UUID activityForArchive;
    private UUID activityForActivate;
    private UUID activityForBlock;
    private UUID activityForChildVariants;
    private UUID variantChildOfActivity;
    private UUID tagForRename;

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

        // Create admin user
        adminUser = new User();
        adminUser.setEmail("admin@test.com");
        adminUser.setPasswordHash(passwordEncoder.encode("admin123"));
        adminUser.setDisplayName("Admin User");
        adminUser.setAdmin(true);
        adminUser = userRepository.save(adminUser);

        // Create regular user
        regularUser = new User();
        regularUser.setEmail("regular@test.com");
        regularUser.setPasswordHash(passwordEncoder.encode("regular123"));
        regularUser.setDisplayName("Regular User");
        regularUser.setAdmin(false);
        regularUser = userRepository.save(regularUser);

        // Resolve category
        sportCategoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE slug = 'sport'", UUID.class);

        // Activity for T7.3 (archive) — start ACTIVE
        activityForArchive = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO activities (id, title, description, category_id, created_by, status) " +
                "VALUES (?::uuid, 'Для архивации', 'Описание', ?::uuid, ?::uuid, 'ACTIVE')",
                activityForArchive.toString(), sportCategoryId.toString(), adminUser.getId().toString());

        // Activity for T7.4 (activate) — start ARCHIVED
        activityForActivate = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO activities (id, title, description, category_id, created_by, status) " +
                "VALUES (?::uuid, 'Для активации', 'Описание', ?::uuid, ?::uuid, 'ARCHIVED')",
                activityForActivate.toString(), sportCategoryId.toString(), adminUser.getId().toString());

        // Activity for T7.5 (block) — start ACTIVE
        activityForBlock = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO activities (id, title, description, category_id, created_by, status) " +
                "VALUES (?::uuid, 'Для блокировки', 'Описание', ?::uuid, ?::uuid, 'ACTIVE')",
                activityForBlock.toString(), sportCategoryId.toString(), adminUser.getId().toString());

        // Activity + variant for T7.8 (child variant not visible after parent archived)
        activityForChildVariants = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO activities (id, title, description, category_id, created_by, status) " +
                "VALUES (?::uuid, 'Родительская активность', 'Описание', ?::uuid, ?::uuid, 'ACTIVE')",
                activityForChildVariants.toString(), sportCategoryId.toString(), adminUser.getId().toString());

        variantChildOfActivity = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO variants (id, activity_id, title, description, created_by, status) " +
                "VALUES (?::uuid, ?::uuid, 'Дочерний вариант', 'Описание варианта', ?::uuid, 'ACTIVE')",
                variantChildOfActivity.toString(), activityForChildVariants.toString(),
                adminUser.getId().toString());

        // Tag for T7.7 (rename)
        tagForRename = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tags (id, slug, name, created_by) VALUES (?::uuid, 'old_slug', 'Старое имя', ?::uuid)",
                tagForRename.toString(), adminUser.getId().toString());
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

    private void loginAdmin() throws Exception {
        var result = mockMvc.perform(formLogin("/login")
                        .user("username", "admin@test.com")
                        .password("admin123"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        adminSession = result.getResponse().getCookie("SESSION");
        assertThat(adminSession).isNotNull();
    }

    private void loginRegular() throws Exception {
        var result = mockMvc.perform(formLogin("/login")
                        .user("username", "regular@test.com")
                        .password("regular123"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        regularSession = result.getResponse().getCookie("SESSION");
        assertThat(regularSession).isNotNull();
    }

    // ---- T7.1 — GET /admin (is_admin=true) → 200 ----

    @Test
    @Order(1)
    @DisplayName("T7.1 — GET /admin (is_admin=true) → 200")
    void adminDashboardReturns200() throws Exception {
        loginAdmin();

        mockMvc.perform(get("/admin").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Админ-панель")));
    }

    // ---- T7.2 — GET /admin (обычный пользователь) → 403 ----

    @Test
    @Order(2)
    @DisplayName("T7.2 — GET /admin (обычный пользователь) → 403")
    void adminDashboardForbiddenForRegularUser() throws Exception {
        loginRegular();

        mockMvc.perform(get("/admin").cookie(regularSession))
                .andExpect(status().isForbidden());
    }

    // ---- T7.3 — POST /admin/activities/{id}/archive → status=ARCHIVED ----

    @Test
    @Order(3)
    @DisplayName("T7.3 — POST /admin/activities/{id}/archive → status=ARCHIVED, не видна в каталоге")
    void archiveActivityChangesStatus() throws Exception {
        loginAdmin();

        // Archive the activity
        mockMvc.perform(post("/admin/activities/{id}/archive", activityForArchive)
                        .cookie(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        // Verify status in DB
        String status = jdbcTemplate.queryForObject(
                "SELECT status::text FROM activities WHERE id = ?::uuid",
                String.class, activityForArchive.toString());
        assertThat(status).isEqualTo("ARCHIVED");

        // Verify NOT visible in visible_activities view
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visible_activities WHERE id = ?::uuid",
                Integer.class, activityForArchive.toString());
        assertThat(count).isEqualTo(0);
    }

    // ---- T7.4 — POST /admin/activities/{id}/activate → status=ACTIVE ----

    @Test
    @Order(4)
    @DisplayName("T7.4 — POST /admin/activities/{id}/activate → status=ACTIVE, снова видна")
    void activateActivityChangesStatus() throws Exception {
        loginAdmin();

        // Activate the archived activity
        mockMvc.perform(post("/admin/activities/{id}/activate", activityForActivate)
                        .cookie(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        // Verify status in DB
        String status = jdbcTemplate.queryForObject(
                "SELECT status::text FROM activities WHERE id = ?::uuid",
                String.class, activityForActivate.toString());
        assertThat(status).isEqualTo("ACTIVE");

        // Verify visible in visible_activities view
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visible_activities WHERE id = ?::uuid",
                Integer.class, activityForActivate.toString());
        assertThat(count).isEqualTo(1);
    }

    // ---- T7.5 — POST /admin/activities/{id}/block → status=BLOCKED ----

    @Test
    @Order(5)
    @DisplayName("T7.5 — POST /admin/activities/{id}/block → status=BLOCKED, не видна")
    void blockActivityChangesStatus() throws Exception {
        loginAdmin();

        // Block the activity
        mockMvc.perform(post("/admin/activities/{id}/block", activityForBlock)
                        .cookie(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        // Verify status in DB
        String status = jdbcTemplate.queryForObject(
                "SELECT status::text FROM activities WHERE id = ?::uuid",
                String.class, activityForBlock.toString());
        assertThat(status).isEqualTo("BLOCKED");

        // Verify NOT visible in visible_activities view
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visible_activities WHERE id = ?::uuid",
                Integer.class, activityForBlock.toString());
        assertThat(count).isEqualTo(0);
    }

    // ---- T7.6 — POST /reports с валидными данными → 200 ----

    @Test
    @Order(6)
    @DisplayName("T7.6 — POST /reports с валидными данными → 200")
    void reportReturns200() throws Exception {
        loginRegular();

        mockMvc.perform(post("/reports")
                        .cookie(regularSession)
                        .with(csrf())
                        .param("target_type", "activity")
                        .param("target_id", activityForArchive.toString())
                        .param("reason", "duplicate")
                        .param("comment", "Похоже на существующую активность"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Спасибо")));

        // Also test invalid reason → 400
        mockMvc.perform(post("/reports")
                        .cookie(regularSession)
                        .with(csrf())
                        .param("target_type", "activity")
                        .param("target_id", activityForArchive.toString())
                        .param("reason", "invalid_reason"))
                .andExpect(status().isBadRequest());
    }

    // ---- T7.7 — POST /admin/tags/{id}/rename → slug нормализован ----

    @Test
    @Order(7)
    @DisplayName("T7.7 — POST /admin/tags/{id}/rename → slug нормализован")
    void renameTagNormalizesSlug() throws Exception {
        loginAdmin();

        // Rename with mixed case and special characters
        mockMvc.perform(post("/admin/tags/{id}/rename", tagForRename)
                        .cookie(adminSession)
                        .with(csrf())
                        .param("name", "  Новое ИМЯ!!!  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        // Verify name and slug in DB
        var row = jdbcTemplate.queryForMap(
                "SELECT name, slug FROM tags WHERE id = ?::uuid",
                tagForRename.toString());
        assertThat(row.get("name")).isEqualTo("Новое ИМЯ!!!");
        // Slug should be normalized: lowercase, non-alphanumeric → underscore, trimmed
        assertThat(row.get("slug").toString()).isEqualTo("новое_имя");
    }

    // ---- T7.8 — Дочерние варианты архивированной активити не видны ----

    @Test
    @Order(8)
    @DisplayName("T7.8 — Дочерние варианты архивированной активити не видны (без изменения их status)")
    void childVariantNotVisibleAfterParentArchived() throws Exception {
        loginAdmin();

        // Before archiving: variant should be visible
        Integer visibleBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visible_variants WHERE id = ?::uuid",
                Integer.class, variantChildOfActivity.toString());
        assertThat(visibleBefore).isEqualTo(1);

        // Verify variant status is still ACTIVE in base table
        String variantStatus = jdbcTemplate.queryForObject(
                "SELECT status::text FROM variants WHERE id = ?::uuid",
                String.class, variantChildOfActivity.toString());
        assertThat(variantStatus).isEqualTo("ACTIVE");

        // Archive the parent activity
        mockMvc.perform(post("/admin/activities/{id}/archive", activityForChildVariants)
                        .cookie(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        // Verify parent activity status in base table
        String parentStatus = jdbcTemplate.queryForObject(
                "SELECT status::text FROM activities WHERE id = ?::uuid",
                String.class, activityForChildVariants.toString());
        assertThat(parentStatus).isEqualTo("ARCHIVED");

        // Variant's own status should NOT have changed
        variantStatus = jdbcTemplate.queryForObject(
                "SELECT status::text FROM variants WHERE id = ?::uuid",
                String.class, variantChildOfActivity.toString());
        assertThat(variantStatus).isEqualTo("ACTIVE");

        // But variant should NOT be visible via visible_variants view
        Integer visibleAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visible_variants WHERE id = ?::uuid",
                Integer.class, variantChildOfActivity.toString());
        assertThat(visibleAfter).isEqualTo(0);
    }
}
