package com.lep.portal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;

/**
 * Enables Spring Data JDBC Auditing.
 * {@code @CreatedDate} and {@code @LastModifiedDate} on entity fields
 * are automatically populated by the framework — the application
 * owns timestamps, not the database.
 */
@Configuration
@EnableJdbcAuditing
public class JdbcAuditingConfig {
}
