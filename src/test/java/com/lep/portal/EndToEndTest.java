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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.lep.portal.invite.Invite;
import com.lep.portal.invite.InviteRepository;
import com.lep.portal.user.User;
import com.lep.portal.user.UserRepository;

import software.amazon.awssdk.services.s3.S3Client;

/**
 * End-to-end smoke test: full user journey across all modules.
 *
 * E2E.1 — Create invite → register → login
 * E2E.2 — Create Activity → visible in catalog
 * E2E.3 — Create Variant for Activity
 * E2E.4 — Set status WANT_TO_TRY on Activity
 * E2E.5 — Add to bookmarks
 * E2E.6 — Second user sees first user in Friends section
 * E2E.7 — Admin archives Activity → disappears from catalog
 * E2E.8 — Set WANT_TO_TRY and toggle bookmark on Variant level
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EndToEndTest {

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

    @MockBean
    private S3Client s3Client;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private InviteRepository inviteRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---- E2E.1 state ----
    private User seedUser;
    private jakarta.servlet.http.Cookie seedSession;
    private String inviteCode;
    private User newUser;
    private jakarta.servlet.http.Cookie newUserSession;

    // ---- E2E.2-E2E.5 state ----
    private UUID sportCategoryId;
    private UUID activityId;
    private UUID variantId;

    // ---- E2E.6 state ----
    private User secondUser;
    private jakarta.servlet.http.Cookie secondUserSession;

    // ---- E2E.7 state ----
    private User adminUser;
    private jakarta.servlet.http.Cookie adminSession;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM bookmarks");
        jdbcTemplate.update("DELETE FROM user_experiences");
        jdbcTemplate.update("DELETE FROM variant_tags");
        jdbcTemplate.update("DELETE FROM activity_tags");
        jdbcTemplate.update("DELETE FROM variants");
        jdbcTemplate.update("DELETE FROM activities");
        jdbcTemplate.update("DELETE FROM tags");
        jdbcTemplate.update("DELETE FROM invites");
        jdbcTemplate.update("DELETE FROM users");

        // Create seed user (who will generate invites)
        seedUser = new User();
        seedUser.setEmail("seed@e2e.com");
        seedUser.setPasswordHash(passwordEncoder.encode("password123"));
        seedUser.setDisplayName("Seed User");
        seedUser.setAdmin(false);
        seedUser = userRepository.save(seedUser);

        // Resolve category
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

    // ==================================================================
    // E2E.1 — Create invite → register → login
    // ==================================================================

    @Test
    @Order(1)
    @DisplayName("E2E.1 — Создать инвайт-код → зарегистрироваться по нему → войти")
    void inviteRegisterLoginFlow() throws Exception {
        // --- Step 1: seed user logs in ---
        var loginResult = mockMvc.perform(formLogin("/login")
                        .user("username", "seed@e2e.com")
                        .password("password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();
        seedSession = loginResult.getResponse().getCookie("SESSION");
        assertThat(seedSession).isNotNull();

        // --- Step 2: seed user generates invite ---
        String redirectUrl = mockMvc.perform(post("/invites/generate")
                        .cookie(seedSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/invites/*"))
                .andReturn()
                .getResponse()
                .getRedirectedUrl();

        UUID inviteId = UUID.fromString(redirectUrl.substring(redirectUrl.lastIndexOf('/') + 1));
        Invite invite = inviteRepository.findById(inviteId).orElseThrow();
        inviteCode = invite.getCode();
        assertThat(inviteCode).isNotBlank();

        // --- Step 3: new user registers with invite code ---
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("inviteCode", inviteCode)
                        .param("displayName", "New E2E User")
                        .param("email", "newuser@e2e.com")
                        .param("password", "password123")
                        .param("consent", "true")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        // Verify user exists in DB
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) > 0 FROM users WHERE email = ? AND deleted_at IS NULL",
                Boolean.class, "newuser@e2e.com");
        assertThat(exists).isTrue();

        // --- Step 4: new user logs in ---
        var newLoginResult = mockMvc.perform(formLogin("/login")
                        .user("username", "newuser@e2e.com")
                        .password("password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();
        newUserSession = newLoginResult.getResponse().getCookie("SESSION");
        assertThat(newUserSession).isNotNull();

        // Load new user for later use
        newUser = userRepository.findByEmail("newuser@e2e.com").orElseThrow();
    }

    // ==================================================================
    // E2E.2 — Create Activity → visible in catalog
    // ==================================================================

    @Test
    @Order(2)
    @DisplayName("E2E.2 — Создать Activity → убедиться что видна в каталоге")
    void createActivityAndVerifyVisible() throws Exception {
        // Recreate E2E.1 state (invite + register + login) so user exists
        recreateE2E1AndE2State();

        // Verify the activity was created by recreateE2E1AndE2State
        assertThat(activityId).isNotNull();

        // Verify in DB
        String status = jdbcTemplate.queryForObject(
                "SELECT status::text FROM activities WHERE id = ?::uuid",
                String.class, activityId.toString());
        assertThat(status).isEqualTo("ACTIVE");

        // Verify visible in catalog
        mockMvc.perform(get("/").cookie(newUserSession))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Скалолазание E2E")));

        // Verify visible via visible_activities view
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visible_activities WHERE id = ?::uuid",
                Integer.class, activityId.toString());
        assertThat(count).isEqualTo(1);
    }

    // ==================================================================
    // E2E.3 — Create Variant for Activity
    // ==================================================================

    @Test
    @Order(3)
    @DisplayName("E2E.3 — Создать Variant к Activity")
    void createVariantForActivity() throws Exception {
        // Re-create state (tests are isolated in Spring context but we re-use IDs;
        // setUp/tearDown clean between tests, so we need to recreate)
        recreateE2E1AndE2State();

        String redirectUrl = mockMvc.perform(post("/activities/" + activityId + "/variants")
                        .cookie(newUserSession)
                        .with(csrf())
                        .param("title", "Скалолазание с инструктором")
                        .param("description", "С профессиональным гидом")
                        .param("differenceReason", "С инструктором безопаснее для новичков"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/activities/" + activityId + "/variants/*"))
                .andReturn()
                .getResponse()
                .getRedirectedUrl();

        variantId = UUID.fromString(redirectUrl.substring(redirectUrl.lastIndexOf('/') + 1));

        // Verify variant exists and is ACTIVE
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM variants WHERE id = ?::uuid AND status = 'ACTIVE'",
                Integer.class, variantId.toString());
        assertThat(count).isEqualTo(1);

        // Verify variant visible on activity detail page
        mockMvc.perform(get("/activities/" + activityId).cookie(newUserSession))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Скалолазание с инструктором")));
    }

    // ==================================================================
    // E2E.4 — Set status WANT_TO_TRY on Activity
    // ==================================================================

    @Test
    @Order(4)
    @DisplayName("E2E.4 — Поставить статус WANT_TO_TRY на Activity")
    void setWantToTryStatus() throws Exception {
        recreateE2E1AndE2State();

        mockMvc.perform(post("/experiences")
                        .cookie(newUserSession)
                        .with(csrf())
                        .param("activityId", activityId.toString())
                        .param("variantId", "")
                        .param("status", "WANT_TO_TRY"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("status-buttons")));

        // Verify row persisted
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_experiences WHERE user_id = ?::uuid AND activity_id = ?::uuid AND variant_id IS NULL",
                Integer.class, newUser.getId().toString(), activityId.toString());
        assertThat(count).isEqualTo(1);

        String uxStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM user_experiences WHERE user_id = ?::uuid AND activity_id = ?::uuid AND variant_id IS NULL",
                String.class, newUser.getId().toString(), activityId.toString());
        assertThat(uxStatus).isEqualTo("WANT_TO_TRY");
    }

    // ==================================================================
    // E2E.5 — Add to bookmarks
    // ==================================================================

    @Test
    @Order(5)
    @DisplayName("E2E.5 — Добавить в закладки (и удалить)")
    void toggleBookmark() throws Exception {
        recreateE2E1AndE2State();

        // Create bookmark
        mockMvc.perform(post("/bookmarks/toggle")
                        .cookie(newUserSession)
                        .with(csrf())
                        .param("activityId", activityId.toString())
                        .param("variantId", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("bookmark-button")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("В избранном")));

        // Verify in DB
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bookmarks WHERE user_id = ?::uuid AND activity_id = ?::uuid",
                Integer.class, newUser.getId().toString(), activityId.toString());
        assertThat(count).isEqualTo(1);

        // Toggle off
        mockMvc.perform(post("/bookmarks/toggle")
                        .cookie(newUserSession)
                        .with(csrf())
                        .param("activityId", activityId.toString())
                        .param("variantId", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("В избранное")));

        count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bookmarks WHERE user_id = ?::uuid AND activity_id = ?::uuid",
                Integer.class, newUser.getId().toString(), activityId.toString());
        assertThat(count).isEqualTo(0);
    }

    // ==================================================================
    // E2E.6 — Second user sees first user in Friends section
    // ==================================================================

    @Test
    @Order(6)
    @DisplayName("E2E.6 — Второй пользователь видит первого в Friends-разделе")
    void secondUserSeesFriendsActivity() throws Exception {
        recreateE2E1AndE2State();

        // First user (newUser) sets WANT_TO_TRY on activity
        jdbcTemplate.update(
                "INSERT INTO user_experiences (id, user_id, activity_id, status) " +
                "VALUES (?::uuid, ?::uuid, ?::uuid, 'WANT_TO_TRY')",
                UUID.randomUUID().toString(), newUser.getId().toString(), activityId.toString());

        // Create second user
        secondUser = new User();
        secondUser.setEmail("second@e2e.com");
        secondUser.setPasswordHash(passwordEncoder.encode("password123"));
        secondUser.setDisplayName("Второй Пользователь");
        secondUser.setAdmin(false);
        secondUser = userRepository.save(secondUser);

        var secondLogin = mockMvc.perform(formLogin("/login")
                        .user("username", "second@e2e.com")
                        .password("password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();
        secondUserSession = secondLogin.getResponse().getCookie("SESSION");
        assertThat(secondUserSession).isNotNull();

        // Second user visits main page — should see first user in Friends section
        mockMvc.perform(get("/").cookie(secondUserSession))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Что делают другие")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("New E2E User")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Скалолазание E2E")));
    }

    // ==================================================================
    // E2E.7 — Admin archives Activity → disappears from catalog
    // ==================================================================

    @Test
    @Order(7)
    @DisplayName("E2E.7 — Admin архивирует Activity → она исчезает из каталога")
    void adminArchivesActivityDisappears() throws Exception {
        recreateE2E1AndE2State();

        // Verify activity is visible before archiving
        Integer beforeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visible_activities WHERE id = ?::uuid",
                Integer.class, activityId.toString());
        assertThat(beforeCount).isEqualTo(1);

        // Create admin user
        adminUser = new User();
        adminUser.setEmail("admin@e2e.com");
        adminUser.setPasswordHash(passwordEncoder.encode("admin123"));
        adminUser.setDisplayName("E2E Admin");
        adminUser.setAdmin(true);
        adminUser = userRepository.save(adminUser);

        var adminLogin = mockMvc.perform(formLogin("/login")
                        .user("username", "admin@e2e.com")
                        .password("admin123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();
        adminSession = adminLogin.getResponse().getCookie("SESSION");
        assertThat(adminSession).isNotNull();

        // Admin archives the activity
        mockMvc.perform(post("/admin/activities/" + activityId + "/archive")
                        .cookie(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        // Verify status changed to ARCHIVED
        String newStatus = jdbcTemplate.queryForObject(
                "SELECT status::text FROM activities WHERE id = ?::uuid",
                String.class, activityId.toString());
        assertThat(newStatus).isEqualTo("ARCHIVED");

        // Verify NOT visible in catalog (via view)
        Integer afterCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visible_activities WHERE id = ?::uuid",
                Integer.class, activityId.toString());
        assertThat(afterCount).isEqualTo(0);

        // Verify 404 when accessing directly
        mockMvc.perform(get("/activities/" + activityId).cookie(newUserSession))
                .andExpect(status().isNotFound());
    }

    // ==================================================================
    // E2E.8 — Set WANT_TO_TRY and toggle bookmark on Variant level
    // ==================================================================

    @Test
    @Order(8)
    @DisplayName("E2E.8 — Поставить WANT_TO_TRY и закладку на уровне Variant")
    void variantLevelExperienceAndBookmark() throws Exception {
        recreateE2E1AndE2State();

        // Create variant first
        String variantRedirect = mockMvc.perform(post("/activities/" + activityId + "/variants")
                        .cookie(newUserSession)
                        .with(csrf())
                        .param("title", "Скалолазание с инструктором E2E8")
                        .param("description", "С профессиональным гидом")
                        .param("differenceReason", "Безопаснее для новичков"))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
        UUID vId = UUID.fromString(variantRedirect.substring(variantRedirect.lastIndexOf('/') + 1));

        // Set WANT_TO_TRY on variant level
        mockMvc.perform(post("/experiences")
                        .cookie(newUserSession)
                        .with(csrf())
                        .param("activityId", activityId.toString())
                        .param("variantId", vId.toString())
                        .param("status", "WANT_TO_TRY"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("status-buttons")));

        // Verify variant-level row persisted
        Integer expCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_experiences WHERE user_id = ?::uuid AND variant_id = ?::uuid",
                Integer.class, newUser.getId().toString(), vId.toString());
        assertThat(expCount).isEqualTo(1);

        // Toggle bookmark ON for the variant
        mockMvc.perform(post("/bookmarks/toggle")
                        .cookie(newUserSession)
                        .with(csrf())
                        .param("activityId", activityId.toString())
                        .param("variantId", vId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("В избранном")));

        // Verify variant-level bookmark exists
        Integer bmCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bookmarks WHERE user_id = ?::uuid AND variant_id = ?::uuid",
                Integer.class, newUser.getId().toString(), vId.toString());
        assertThat(bmCount).isEqualTo(1);

        // Toggle bookmark OFF
        mockMvc.perform(post("/bookmarks/toggle")
                        .cookie(newUserSession)
                        .with(csrf())
                        .param("activityId", activityId.toString())
                        .param("variantId", vId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("В избранное")));

        // Verify bookmark removed
        bmCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bookmarks WHERE user_id = ?::uuid AND variant_id = ?::uuid",
                Integer.class, newUser.getId().toString(), vId.toString());
        assertThat(bmCount).isEqualTo(0);
    }

    // ==================================================================
    // Helper methods
    // ==================================================================

    /**
     * Re-create E2E.1 (invite + register + login) and E2E.2 (create activity) state
     * so each @Test can run independently.
     */
    private void recreateE2E1AndE2State() throws Exception {
        // --- E2E.1: invite + register ---
        var loginResult = mockMvc.perform(formLogin("/login")
                        .user("username", "seed@e2e.com")
                        .password("password123"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        seedSession = loginResult.getResponse().getCookie("SESSION");
        assertThat(seedSession).isNotNull();

        // Generate invite
        String inviteRedirect = mockMvc.perform(post("/invites/generate")
                        .cookie(seedSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
        UUID inviteId = UUID.fromString(inviteRedirect.substring(inviteRedirect.lastIndexOf('/') + 1));
        inviteCode = inviteRepository.findById(inviteId).orElseThrow().getCode();

        // Check if new user already exists (may have been created by E2E.1 test)
        var existingNewUser = userRepository.findByEmail("newuser@e2e.com");
        if (existingNewUser.isEmpty()) {
            // Register new user
            mockMvc.perform(post("/register")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("inviteCode", inviteCode)
                            .param("displayName", "New E2E User")
                            .param("email", "newuser@e2e.com")
                            .param("password", "password123")
                            .param("consent", "true")
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }

        // Login as new user
        loginNewUser();
        newUser = userRepository.findByEmail("newuser@e2e.com").orElseThrow();

        // --- E2E.2: create activity if not present ---
        Integer existingActivity = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activities WHERE title = 'Скалолазание E2E' AND deleted_at IS NULL",
                Integer.class);
        if (existingActivity != null && existingActivity == 0) {
            String activityRedirect = mockMvc.perform(post("/activities")
                            .cookie(newUserSession)
                            .with(csrf())
                            .param("title", "Скалолазание E2E")
                            .param("description", "Лазание по скалам на природе")
                            .param("categoryId", sportCategoryId.toString())
                            .param("complexity", "high")
                            .param("costTier", "mid"))
                    .andExpect(status().is3xxRedirection())
                    .andReturn()
                    .getResponse()
                    .getRedirectedUrl();
            activityId = UUID.fromString(activityRedirect.substring(activityRedirect.lastIndexOf('/') + 1));
        } else {
            activityId = jdbcTemplate.queryForObject(
                    "SELECT id FROM activities WHERE title = 'Скалолазание E2E' AND deleted_at IS NULL",
                    UUID.class);
        }
    }

    private void loginNewUser() throws Exception {
        var result = mockMvc.perform(formLogin("/login")
                        .user("username", "newuser@e2e.com")
                        .password("password123"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        newUserSession = result.getResponse().getCookie("SESSION");
        assertThat(newUserSession).isNotNull();
    }
}
