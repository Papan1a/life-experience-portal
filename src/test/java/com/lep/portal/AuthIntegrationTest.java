package com.lep.portal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("leptest")
            .withUsername("leptest")
            .withPassword("leptest");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl() + "&stringtype=unspecified");
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
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User seedUser;
    private String validInviteCode;
    private String expiredInviteCode;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM bookmarks");
        jdbcTemplate.update("DELETE FROM user_experiences");
        jdbcTemplate.update("DELETE FROM variant_tags");
        jdbcTemplate.update("DELETE FROM activity_tags");
        jdbcTemplate.update("DELETE FROM variants");
        jdbcTemplate.update("DELETE FROM activities");
        jdbcTemplate.update("DELETE FROM invites");
        jdbcTemplate.update("DELETE FROM users");

        seedUser = new User();
        seedUser.setEmail("seed@test.com");
        seedUser.setPasswordHash(passwordEncoder.encode("password123"));
        seedUser.setDisplayName("Seed User");
        seedUser.setAdmin(false);
        seedUser = userRepository.save(seedUser);

        Invite validInvite = new Invite();
        validInvite.setCode("valid-invite-code");
        validInvite.setCreatedBy(seedUser.getId());
        validInvite.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        validInvite = inviteRepository.save(validInvite);
        validInviteCode = validInvite.getCode();

        Invite expiredInvite = new Invite();
        expiredInvite.setCode("expired-invite-code");
        expiredInvite.setCreatedBy(seedUser.getId());
        expiredInvite.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        inviteRepository.save(expiredInvite);
        expiredInviteCode = expiredInvite.getCode();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM bookmarks");
        jdbcTemplate.update("DELETE FROM user_experiences");
        jdbcTemplate.update("DELETE FROM variant_tags");
        jdbcTemplate.update("DELETE FROM activity_tags");
        jdbcTemplate.update("DELETE FROM variants");
        jdbcTemplate.update("DELETE FROM activities");
        jdbcTemplate.update("DELETE FROM invites");
        jdbcTemplate.update("DELETE FROM users");
    }

    // ---- T2.1 ----
    @Test
    @Order(1)
    @DisplayName("T2.1 — GET /login без сессии → 200")
    void loginPageAvailable() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Life Experience Portal")));
    }

    // ---- T2.2 ----
    @Test
    @Order(2)
    @DisplayName("T2.2 — POST /login с неверным паролем → redirect /login?error")
    void loginWithWrongPasswordRedirectsToError() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "seed@test.com")
                        .param("password", "wrongpassword")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    // ---- T2.3 ----
    @Test
    @Order(3)
    @DisplayName("T2.3 — GET /register без кода → страница с сообщением об ошибке")
    void registerWithoutCodeShowsError() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Для регистрации необходим код приглашения")));
    }

    // ---- T2.4 ----
    @Test
    @Order(4)
    @DisplayName("T2.4 — POST /register с несуществующим инвайтом → ошибка на форме")
    void registerWithBadInviteShowsError() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("inviteCode", "nonexistent-code")
                        .param("displayName", "Test User")
                        .param("email", "test@test.com")
                        .param("password", "password123")
                        .param("consent", "true")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register?code=nonexistent-code"))
                .andExpect(flash().attributeExists("error"));
    }

    // ---- T2.4b ----
    @Test
    @Order(5)
    @DisplayName("T2.4b — POST /register с просроченным инвайтом → ошибка на форме")
    void registerWithExpiredInviteShowsError() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("inviteCode", expiredInviteCode)
                        .param("displayName", "Test User")
                        .param("email", "test2@test.com")
                        .param("password", "password123")
                        .param("consent", "true")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register?code=" + expiredInviteCode))
                .andExpect(flash().attributeExists("error"));
    }

    // ---- T2.5 ----
    @Test
    @Order(6)
    @DisplayName("T2.5 — POST /register с валидным инвайтом → пользователь создан, redirect /")
    void registerWithValidInviteCreatesUser() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("inviteCode", validInviteCode)
                        .param("displayName", "New User")
                        .param("email", "newuser@test.com")
                        .param("password", "password123")
                        .param("consent", "true")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        // Verify user exists in DB via raw JDBC (avoids PGobject mapping issues)
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) > 0 FROM users WHERE email = ? AND deleted_at IS NULL",
                Boolean.class, "newuser@test.com");
        assertThat(exists).isTrue();

        String displayName = jdbcTemplate.queryForObject(
                "SELECT display_name FROM users WHERE email = ? AND deleted_at IS NULL",
                String.class, "newuser@test.com");
        assertThat(displayName).isEqualTo("New User");

        // Verify invite_id and invited_by_user_id
        String inviteIdStr = jdbcTemplate.queryForObject(
                "SELECT invite_id::text FROM users WHERE email = ? AND deleted_at IS NULL",
                String.class, "newuser@test.com");
        assertThat(inviteIdStr).isNotNull();

        String invitedByStr = jdbcTemplate.queryForObject(
                "SELECT invited_by_user_id::text FROM users WHERE email = ? AND deleted_at IS NULL",
                String.class, "newuser@test.com");
        assertThat(invitedByStr).isEqualTo(seedUser.getId().toString());
    }

    // ---- T2.6 ----
    @Test
    @Order(7)
    @DisplayName("T2.6 — POST /register с тем же email повторно → ошибка на форме")
    void registerWithDuplicateEmailShowsError() throws Exception {
        // First registration
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("inviteCode", validInviteCode)
                        .param("displayName", "First User")
                        .param("email", "dupe@test.com")
                        .param("password", "password123")
                        .param("consent", "true")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        // Second registration with same email — needs a fresh invite
        Invite secondInvite = new Invite();
        secondInvite.setCode("second-invite-code");
        secondInvite.setCreatedBy(seedUser.getId());
        secondInvite.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        inviteRepository.save(secondInvite);

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("inviteCode", "second-invite-code")
                        .param("displayName", "Second User")
                        .param("email", "dupe@test.com")
                        .param("password", "password123")
                        .param("consent", "true")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register?code=second-invite-code"))
                .andExpect(flash().attributeExists("error"));
    }

    // ---- T2.7 ----
    @Test
    @Order(8)
    @DisplayName("T2.7 — GET / без сессии → redirect /login")
    void homeWithoutSessionRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // ---- T2.8 ----
    @Test
    @Order(9)
    @DisplayName("T2.8 — POST /invites/generate (авторизован) → инвайт создан, TTL=7 дней")
    void createInviteAsAuthenticatedUser() throws Exception {
        // Login via formLogin and capture the SESSION cookie from the response.
        // spring-session-jdbc stores sessions in DB; passing the cookie reattaches the same session.
        var loginResult = mockMvc.perform(formLogin("/login")
                        .user("username", "seed@test.com")
                        .password("password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        jakarta.servlet.http.Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();

        mockMvc.perform(post("/invites/generate")
                        .cookie(sessionCookie)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/invites/*"));

        // Verify invite in DB via JDBC
        var createdByStr = seedUser.getId().toString();
        Integer inviteCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invites WHERE created_by = ?::uuid",
                Integer.class, createdByStr);
        // setUp creates 2 invites + this one = 3
        assertThat(inviteCount).isGreaterThanOrEqualTo(3);

        // Check the newest invite has correct TTL
        String code = jdbcTemplate.queryForObject(
                "SELECT code FROM invites WHERE created_by = ?::uuid "
                        + "AND code NOT IN ('valid-invite-code','expired-invite-code') "
                        + "ORDER BY created_at DESC LIMIT 1",
                String.class, createdByStr);
        assertThat(code).isNotNull();

        java.sql.Timestamp expiresAt = jdbcTemplate.queryForObject(
                "SELECT expires_at FROM invites WHERE code = ?",
                java.sql.Timestamp.class, code);
        assertThat(expiresAt).isNotNull();
        long days = ChronoUnit.DAYS.between(Instant.now(), expiresAt.toInstant());
        assertThat(days).isBetween(6L, 7L);
    }
}
