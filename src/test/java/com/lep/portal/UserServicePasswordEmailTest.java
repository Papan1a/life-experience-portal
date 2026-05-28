package com.lep.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.lep.portal.user.User;
import com.lep.portal.user.UserRepository;
import com.lep.portal.user.UserService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserServicePasswordEmailTest {

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
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User seedUser;
    private User otherUser;

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
        seedUser.setEmail("user@test.com");
        seedUser.setPasswordHash(passwordEncoder.encode("currentPass1"));
        seedUser.setDisplayName("Test User");
        seedUser = userRepository.save(seedUser);

        otherUser = new User();
        otherUser.setEmail("taken@test.com");
        otherUser.setPasswordHash(passwordEncoder.encode("password123"));
        otherUser.setDisplayName("Other User");
        otherUser = userRepository.save(otherUser);
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

    @Test
    @Order(1)
    @DisplayName("T12.1 — changePassword: успешная смена — можно войти с новым паролем")
    void changePasswordSuccess() {
        userService.changePassword(seedUser.getId(), "currentPass1", "newPassword9");

        User updated = userRepository.findActiveById(seedUser.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("newPassword9", updated.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("currentPass1", updated.getPasswordHash())).isFalse();
    }

    @Test
    @Order(2)
    @DisplayName("T12.2 — changePassword: неверный текущий пароль → IllegalArgumentException")
    void changePasswordWrongCurrent() {
        assertThatThrownBy(() ->
                userService.changePassword(seedUser.getId(), "wrongPassword", "newPassword9"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Неверный текущий пароль");
    }

    @Test
    @Order(3)
    @DisplayName("T12.3 — changePassword: новый пароль совпадает с текущим → IllegalArgumentException")
    void changePasswordSameAsCurrentRejected() {
        assertThatThrownBy(() ->
                userService.changePassword(seedUser.getId(), "currentPass1", "currentPass1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Новый пароль должен отличаться от текущего");
    }

    @Test
    @Order(4)
    @DisplayName("T12.4 — changeEmail: email занят другим пользователем → IllegalArgumentException")
    void changeEmailTakenByOther() {
        assertThatThrownBy(() ->
                userService.changeEmail(seedUser.getId(), "taken@test.com", "currentPass1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Этот email уже занят");
    }

    @Test
    @Order(5)
    @DisplayName("T12.5 — changeEmail: пользователь отправляет свой же email → успешно, без ошибки")
    void changeEmailSameAsOwnSucceeds() {
        userService.changeEmail(seedUser.getId(), "user@test.com", "currentPass1");

        User updated = userRepository.findActiveById(seedUser.getId()).orElseThrow();
        assertThat(updated.getEmail()).isEqualTo("user@test.com");
    }
}
