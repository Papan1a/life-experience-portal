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
public class MyActivitiesIntegrationTest {

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
    private UUID sportCategoryId;

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

        seedUser = new User();
        seedUser.setDisplayName("Тестовый Автор");
        seedUser.setEmail("testauthor@test.dev");
        seedUser.setPasswordHash(passwordEncoder.encode("password"));
        seedUser = userRepository.save(seedUser);

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

    // ---- T1: Залогиненный пользователь открывает /my-activities — видит свои активности ----

    @Test
    @Order(1)
    @DisplayName("T1: Authenticated user sees own activities on /my-activities")
    void authenticatedUserSeesOwnActivities() throws Exception {
        var result = mockMvc.perform(formLogin("/login")
                        .user("username", seedUser.getEmail())
                        .password("password"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        var sessionCookie = result.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();

        // Create an activity as the user
        mockMvc.perform(post("/activities")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .param("title", "Моя тестовая активность")
                        .param("description", "Описание")
                        .param("categoryId", sportCategoryId.toString())
                        .param("complexity", "low")
                        .param("costTier", "free")
                        .param("force", "true"))
                .andExpect(status().is3xxRedirection());

        // Visit /my-activities
        mockMvc.perform(get("/my-activities")
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Моя тестовая активность")));
    }

    // ---- T2: Список пустой, если пользователь ничего не создавал ----

    @Test
    @Order(2)
    @DisplayName("T2: Empty list when user created nothing")
    void emptyListWhenNoActivities() throws Exception {
        var result = mockMvc.perform(formLogin("/login")
                        .user("username", seedUser.getEmail())
                        .password("password"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        var sessionCookie = result.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();

        mockMvc.perform(get("/my-activities")
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Вы ещё не создавали активности")));
    }

    // ---- T3: Варианты к чужим активностям показываются отдельным блоком ----

    @Test
    @Order(3)
    @DisplayName("T3: Variants on others' activities shown in separate block")
    void variantsOnOthersActivitiesShownSeparately() throws Exception {
        // Login as seedUser first
        var result = mockMvc.perform(formLogin("/login")
                        .user("username", seedUser.getEmail())
                        .password("password"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        var sessionCookie = result.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();

        // Create a second user who owns an activity
        var otherUser = new User();
        otherUser.setDisplayName("Другой Автор");
        otherUser.setEmail("other@test.dev");
        otherUser.setPasswordHash(passwordEncoder.encode("password"));
        otherUser = userRepository.save(otherUser);

        // Login as otherUser and create activity
        var otherResult = mockMvc.perform(formLogin("/login")
                        .user("username", otherUser.getEmail())
                        .password("password"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        var otherCookie = otherResult.getResponse().getCookie("SESSION");

        mockMvc.perform(post("/activities")
                        .cookie(otherCookie)
                        .with(csrf())
                        .param("title", "Чужая активность")
                        .param("description", "Описание")
                        .param("categoryId", sportCategoryId.toString())
                        .param("complexity", "low")
                        .param("costTier", "free")
                        .param("force", "true"))
                .andExpect(status().is3xxRedirection());

        // Find the created activity ID
        UUID otherActivityId = jdbcTemplate.queryForObject(
                "SELECT id FROM activities WHERE title = 'Чужая активность'", UUID.class);

        // Login back as seedUser and add variant to other's activity
        result = mockMvc.perform(formLogin("/login")
                        .user("username", seedUser.getEmail())
                        .password("password"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        sessionCookie = result.getResponse().getCookie("SESSION");

        mockMvc.perform(post("/activities/" + otherActivityId + "/variants")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .param("title", "Мой вариант чужой активности")
                        .param("description", "Описание варианта")
                        .param("differenceReason", "Отличие"))
                .andExpect(status().is3xxRedirection());

        // Check /my-activities shows "Мои варианты к чужим активностям" block
        mockMvc.perform(get("/my-activities")
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Мои варианты к чужим активностям")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Мой вариант чужой активности")));
    }

    // ---- T4: Кнопка "Редактировать" ведёт на /activities/{id}/edit ----

    @Test
    @Order(4)
    @DisplayName("T4: Edit button links to /activities/{id}/edit")
    void editButtonLeadsToEditPage() throws Exception {
        var result = mockMvc.perform(formLogin("/login")
                        .user("username", seedUser.getEmail())
                        .password("password"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        var sessionCookie = result.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();

        // Create an activity
        mockMvc.perform(post("/activities")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .param("title", "Редактируемая активность")
                        .param("description", "Описание")
                        .param("categoryId", sportCategoryId.toString())
                        .param("complexity", "low")
                        .param("costTier", "free")
                        .param("force", "true"))
                .andExpect(status().is3xxRedirection());

        UUID activityId = jdbcTemplate.queryForObject(
                "SELECT id FROM activities WHERE title = 'Редактируемая активность'", UUID.class);

        // /my-activities page contains edit link
        mockMvc.perform(get("/my-activities")
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/activities/" + activityId + "/edit")));

        // /activities/{id} page has canEdit button
        mockMvc.perform(get("/activities/" + activityId)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Редактировать")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/activities/" + activityId + "/edit")));
    }

    // ---- T5: Незалогиненный пользователь — редирект на /login ----

    @Test
    @Order(5)
    @DisplayName("T5: Unauthenticated user redirected to /login")
    void unauthenticatedUserRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/my-activities"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // ---- T6: Автор удаляет свою активность → 3xx, исчезает из visible_activities ----

    @Test
    @Order(6)
    @DisplayName("T6: Author deletes own activity → 3xx, disappears from visible_activities")
    void authorDeletesOwnActivity() throws Exception {
        var result = mockMvc.perform(formLogin("/login")
                        .user("username", seedUser.getEmail())
                        .password("password"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        var sessionCookie = result.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();

        // Create an activity
        mockMvc.perform(post("/activities")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .param("title", "Удаляемая активность")
                        .param("description", "Описание")
                        .param("categoryId", sportCategoryId.toString())
                        .param("complexity", "low")
                        .param("costTier", "free")
                        .param("force", "true"))
                .andExpect(status().is3xxRedirection());

        UUID activityId = jdbcTemplate.queryForObject(
                "SELECT id FROM activities WHERE title = 'Удаляемая активность'", UUID.class);

        // Verify it's visible before delete
        Integer visibleCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM visible_activities WHERE id = ?::uuid",
                Integer.class, activityId.toString());
        assertThat(visibleCount).isEqualTo(1);

        // Delete it
        mockMvc.perform(post("/activities/" + activityId + "/delete")
                        .cookie(sessionCookie)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        // Verify it's gone from visible_activities
        visibleCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM visible_activities WHERE id = ?::uuid",
                Integer.class, activityId.toString());
        assertThat(visibleCount).isEqualTo(0);

        // deleted_at is set
        var deletedAt = jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM activities WHERE id = ?::uuid",
                java.sql.Timestamp.class, activityId.toString());
        assertThat(deletedAt).isNotNull();
    }

    // ---- T7: Не-автор не может удалить чужую активность → 403 ----

    @Test
    @Order(7)
    @DisplayName("T7: Non-author cannot delete another's activity → 403")
    void nonAuthorCannotDeleteOthersActivity() throws Exception {
        // Login as seedUser and create activity
        var result = mockMvc.perform(formLogin("/login")
                        .user("username", seedUser.getEmail())
                        .password("password"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        var authorCookie = result.getResponse().getCookie("SESSION");

        mockMvc.perform(post("/activities")
                        .cookie(authorCookie)
                        .with(csrf())
                        .param("title", "Чужая не удаляется")
                        .param("description", "Описание")
                        .param("categoryId", sportCategoryId.toString())
                        .param("complexity", "low")
                        .param("costTier", "free")
                        .param("force", "true"))
                .andExpect(status().is3xxRedirection());

        UUID activityId = jdbcTemplate.queryForObject(
                "SELECT id FROM activities WHERE title = 'Чужая не удаляется'", UUID.class);

        // Create another user (not author, not admin)
        var otherUser = new User();
        otherUser.setDisplayName("Другой Пользователь");
        otherUser.setEmail("stranger@test.dev");
        otherUser.setPasswordHash(passwordEncoder.encode("password"));
        userRepository.save(otherUser);

        result = mockMvc.perform(formLogin("/login")
                        .user("username", otherUser.getEmail())
                        .password("password"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        var strangerCookie = result.getResponse().getCookie("SESSION");

        // Try to delete — should get 403
        mockMvc.perform(post("/activities/" + activityId + "/delete")
                        .cookie(strangerCookie)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ---- T8: Удаление активности скрывает её варианты из visible_variants ----

    @Test
    @Order(8)
    @DisplayName("T8: Deleting activity hides its variants from visible_variants")
    void deletingActivityHidesItsVariants() throws Exception {
        var result = mockMvc.perform(formLogin("/login")
                        .user("username", seedUser.getEmail())
                        .password("password"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        var sessionCookie = result.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();

        // Create an activity
        mockMvc.perform(post("/activities")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .param("title", "Активность с вариантом")
                        .param("description", "Описание")
                        .param("categoryId", sportCategoryId.toString())
                        .param("complexity", "low")
                        .param("costTier", "free")
                        .param("force", "true"))
                .andExpect(status().is3xxRedirection());

        UUID activityId = jdbcTemplate.queryForObject(
                "SELECT id FROM activities WHERE title = 'Активность с вариантом'", UUID.class);

        // Create a variant
        mockMvc.perform(post("/activities/" + activityId + "/variants")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .param("title", "Вариант для удаления")
                        .param("description", "Описание варианта")
                        .param("differenceReason", "Отличие"))
                .andExpect(status().is3xxRedirection());

        UUID variantId = jdbcTemplate.queryForObject(
                "SELECT id FROM variants WHERE title = 'Вариант для удаления'", UUID.class);

        // Variant is visible before delete
        Integer visibleVariants = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM visible_variants WHERE activity_id = ?::uuid",
                Integer.class, activityId.toString());
        assertThat(visibleVariants).isEqualTo(1);

        // Delete the activity
        mockMvc.perform(post("/activities/" + activityId + "/delete")
                        .cookie(sessionCookie)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        // Variants are hidden from visible_variants (view joins with activities.deleted_at IS NULL)
        visibleVariants = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM visible_variants WHERE activity_id = ?::uuid",
                Integer.class, activityId.toString());
        assertThat(visibleVariants).isEqualTo(0);
    }
}
