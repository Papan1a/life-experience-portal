package com.lep.portal;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke test: поднимает полный Spring-контекст с профилем {@code dev}
 * и проверяет, что {@link com.lep.portal.config.BootstrapApplicationRunner}
 * и {@link com.lep.portal.config.DevSeedRunner} отработали корректно.
 * <p>
 * Без HTTP-сервера ({@code WebEnvironment.NONE}) — быстрее, чем
 * полноценные интеграционные тесты, и ловит ошибки на уровне
 * конфигурации контекста и SQL-запросов в раннерах.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dev")
@Testcontainers
@DisplayName("Smoke test: приложение стартует с профилем dev")
public class StartupSmokeTest {

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
        // BootstrapApplicationRunner читает эти ключи напрямую из Environment
        registry.add("ADMIN_BOOTSTRAP_EMAIL", () -> "admin@local.dev");
        registry.add("ADMIN_BOOTSTRAP_PASSWORD", () -> "admin12345");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Контекст загружается, admin создан, dev-seed выполнен")
    void applicationStartsAndSeedsData() {
        // 1. Admin создан (BootstrapApplicationRunner)
        Integer adminCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE is_admin = true AND deleted_at IS NULL",
                Integer.class);
        assertThat(adminCount).isGreaterThan(0);

        // 2. Dev-пользователи созданы (DevSeedRunner)
        Integer devUsers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email IN ('1@1', '2@2')",
                Integer.class);
        assertThat(devUsers).isEqualTo(2);

        // 3. Предсказуемый инвайт существует и не отозван
        Integer inviteCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invites WHERE code = 'test-invite-001' AND revoked_at IS NULL",
                Integer.class);
        assertThat(inviteCount).isEqualTo(1);

        // 4. Активности созданы (insertActivity отработал без ошибок SQL)
        Integer activityCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activities WHERE status = 'ACTIVE'",
                Integer.class);
        assertThat(activityCount).isGreaterThan(0);
    }
}
