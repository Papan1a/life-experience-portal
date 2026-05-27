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
}
