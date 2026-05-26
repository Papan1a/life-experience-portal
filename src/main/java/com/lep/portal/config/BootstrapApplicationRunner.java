package com.lep.portal.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * On application startup, checks whether at least one admin user exists.
 * If not, and the ADMIN_BOOTSTRAP_EMAIL / ADMIN_BOOTSTRAP_PASSWORD env vars
 * are set, creates the admin user automatically.
 * <p>
 * Otherwise, logs a clear instruction for manual admin creation via Adminer.
 */
@Component
public class BootstrapApplicationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapApplicationRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final Environment env;

    public BootstrapApplicationRunner(JdbcTemplate jdbcTemplate,
                                       PasswordEncoder passwordEncoder,
                                       Environment env) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer adminCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE is_admin = true AND deleted_at IS NULL",
                Integer.class);

        if (adminCount != null && adminCount > 0) {
            log.info("Admin user(s) found ({}). Bootstrap skipped.", adminCount);
            return;
        }

        String bootstrapEmail = env.getProperty("ADMIN_BOOTSTRAP_EMAIL");
        String bootstrapPassword = env.getProperty("ADMIN_BOOTSTRAP_PASSWORD");

        if (bootstrapEmail != null && !bootstrapEmail.isBlank()
                && bootstrapPassword != null && !bootstrapPassword.isBlank()) {
            String hash = passwordEncoder.encode(bootstrapPassword);
            jdbcTemplate.update(
                    "INSERT INTO users (email, password_hash, display_name, is_admin, consent_at) " +
                    "VALUES (?, ?, ?, true, NOW())",
                    bootstrapEmail.trim(), hash, "Admin");
            log.info("============================================================");
            log.info("Bootstrap admin created: {}", bootstrapEmail);
            log.info("You can now log in at /login with these credentials.");
            log.info("============================================================");
        } else {
            log.warn("============================================================");
            log.warn("No admin user found in the database.");
            log.warn("To create the first admin, either:");
            log.warn("  1) Set ADMIN_BOOTSTRAP_EMAIL and ADMIN_BOOTSTRAP_PASSWORD env vars and restart.");
            log.warn("  2) Connect via Adminer (http://localhost:8081) and insert manually:");
            log.warn("     INSERT INTO users (email, password_hash, display_name, is_admin, consent_at)");
            log.warn("     VALUES ('admin@example.com', '<ARGON2_HASH>', 'Admin', true, NOW());");
            log.warn("     Generate the Argon2 hash with: ./mvnw exec:java ...");
            log.warn("============================================================");
        }
    }
}
