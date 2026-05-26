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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.lep.portal.user.User;
import com.lep.portal.user.UserRepository;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserProfileIntegrationTest {

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
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private S3Client s3Client;

    private User seedUser;
    private User otherUser;
    private jakarta.servlet.http.Cookie sessionCookie;

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

        // Seed user
        seedUser = new User();
        seedUser.setEmail("profile@test.com");
        seedUser.setPasswordHash(passwordEncoder.encode("password123"));
        seedUser.setDisplayName("Профиль Тест");
        seedUser.setBio("Тестовое био");
        seedUser = userRepository.save(seedUser);

        // Other user
        otherUser = new User();
        otherUser.setEmail("other@test.com");
        otherUser.setPasswordHash(passwordEncoder.encode("password123"));
        otherUser.setDisplayName("Другой Юзер");
        otherUser = userRepository.save(otherUser);

        // Login as seed user
        try {
            var result = mockMvc.perform(formLogin("/login")
                            .user("username", "profile@test.com")
                            .password("password123"))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();
            sessionCookie = result.getResponse().getCookie("SESSION");
            assertThat(sessionCookie).isNotNull();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    // ---- T6.1 ----
    @Test
    @Order(1)
    @DisplayName("T6.1 — GET /users/{id} (авторизован) → 200, данные профиля")
    void viewOwnProfile() throws Exception {
        mockMvc.perform(get("/users/" + seedUser.getId()).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Профиль Тест")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Тестовое био")));
    }

    // ---- T6.2 ----
    @Test
    @Order(2)
    @DisplayName("T6.2 — GET /users/{id} (не авторизован) → redirect /login")
    void viewProfileUnauthorized() throws Exception {
        mockMvc.perform(get("/users/" + seedUser.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // ---- T6.3 ----
    @Test
    @Order(3)
    @DisplayName("T6.3 — GET /users/search?q=Профиль (HTMX) → список пользователей")
    void searchUsers() throws Exception {
        mockMvc.perform(get("/users/search")
                        .param("q", "Профиль")
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Профиль Тест")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Другой Юзер"))));
    }

    // ---- T6.4 ----
    @Test
    @Order(4)
    @DisplayName("T6.4 — GET / содержит Friends-секцию с записями других пользователей")
    void mainPageHasFriendsSection() throws Exception {
        // Other user creates an experience
        jdbcTemplate.update(
                "INSERT INTO activities (id, title, description, category_id, status, created_by) " +
                "VALUES (?::uuid, 'Скалолазание', 'Лазать по скалам', " +
                "(SELECT id FROM categories WHERE slug='sport'), 'ACTIVE', ?::uuid)",
                UUID.randomUUID().toString(), otherUser.getId().toString());

        UUID activityId = jdbcTemplate.queryForObject(
                "SELECT id FROM activities WHERE title = 'Скалолазание'", UUID.class);

        jdbcTemplate.update(
                "INSERT INTO user_experiences (id, user_id, activity_id, status) " +
                "VALUES (?::uuid, ?::uuid, ?::uuid, 'WANT_TO_TRY')",
                UUID.randomUUID().toString(), otherUser.getId().toString(), activityId.toString());

        mockMvc.perform(get("/").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Что делают другие")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Другой Юзер")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("хочет попробовать")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Скалолазание")));
    }

    // ---- T6.5 ----
    @Test
    @Order(5)
    @DisplayName("T6.5 — Friends-секция не содержит записей текущего пользователя")
    void friendsSectionExcludesCurrentUser() throws Exception {
        // Seed user creates an experience
        jdbcTemplate.update(
                "INSERT INTO activities (id, title, description, category_id, status, created_by) " +
                "VALUES (?::uuid, 'Бег', 'Бегать по утрам', " +
                "(SELECT id FROM categories WHERE slug='sport'), 'ACTIVE', ?::uuid)",
                UUID.randomUUID().toString(), seedUser.getId().toString());

        UUID activityId = jdbcTemplate.queryForObject(
                "SELECT id FROM activities WHERE title = 'Бег'", UUID.class);

        jdbcTemplate.update(
                "INSERT INTO user_experiences (id, user_id, activity_id, status) " +
                "VALUES (?::uuid, ?::uuid, ?::uuid, 'TRIED')",
                UUID.randomUUID().toString(), seedUser.getId().toString(), activityId.toString());

        mockMvc.perform(get("/").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("пробовал(а)"))));
    }

    // ---- T6.6 ----
    @Test
    @Order(6)
    @DisplayName("T6.6 — POST /profile/avatar с валидным JPEG → avatar_url обновлён в БД")
    void uploadAvatarUpdatesDb() throws Exception {
        // Mock S3Client to accept any putObject
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        byte[] jpegBytes = new byte[] {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, // JPEG SOI + APP0
            0, 16, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, 0, 0,
            (byte) 0xFF, (byte) 0xDB, 0, 0x43, 0 // DQT marker start
        };
        // Pad to valid minimal JPEG (will be resized by Thumbnailator)
        // Fill with a valid JPEG structure: SOI, DQT, SOF0, DHT, SOS, image data, EOI
        // Using a larger valid minimal JPEG
        byte[] validJpeg = buildMinimalJpeg();

        MockMultipartFile avatar = new MockMultipartFile(
                "avatar", "test.jpg", "image/jpeg", validJpeg);

        mockMvc.perform(multipart("/profile/avatar")
                        .file(avatar)
                        .cookie(sessionCookie)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile/edit"));

        // Verify avatar_url was updated
        User updated = userRepository.findActiveById(seedUser.getId()).orElseThrow();
        assertThat(updated.getAvatarUrl()).isNotNull();
        assertThat(updated.getAvatarUrl()).startsWith("/r2/avatars/");
    }

    private byte[] buildMinimalJpeg() {
        // Build a minimal valid JPEG that Thumbnailator can process
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(10, 10, java.awt.image.BufferedImage.TYPE_INT_RGB);
            javax.imageio.ImageIO.write(img, "JPEG", bos);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return bos.toByteArray();
    }

    // ---- T6.7 ----
    @Test
    @Order(7)
    @DisplayName("T6.7 — POST /profile/avatar с файлом > 2 MB → ошибка")
    void uploadAvatarTooLargeShowsError() throws Exception {
        byte[] largeFile = new byte[3_000_000]; // 3 MB > 2 MB limit
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar", "large.jpg", "image/jpeg", largeFile);

        mockMvc.perform(multipart("/profile/avatar")
                        .file(avatar)
                        .cookie(sessionCookie)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile/edit"));

        // Verify avatar_url was NOT updated
        User updated = userRepository.findActiveById(seedUser.getId()).orElseThrow();
        assertThat(updated.getAvatarUrl()).isNull();
    }

    // ---- T6.8 ----
    @Test
    @Order(8)
    @DisplayName("T6.8 — POST /profile → display_name обновлён")
    void updateProfileChangesDisplayName() throws Exception {
        mockMvc.perform(post("/profile")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("displayName", "Новое Имя")
                        .param("bio", "Новое био")
                        .cookie(sessionCookie)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));

        User updated = userRepository.findActiveById(seedUser.getId()).orElseThrow();
        assertThat(updated.getDisplayName()).isEqualTo("Новое Имя");
        assertThat(updated.getBio()).isEqualTo("Новое био");
    }
}
