package com.lep.portal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

@Configuration
@EnableJdbcHttpSession(tableName = "SPRING_SESSION", maxInactiveIntervalInSeconds = 1209600)
public class SessionConfig {
    // Spring Session JDBC auto-configures JdbcIndexedSessionRepository using the
    // primary DataSource. Table DDL is managed by Flyway V2.
}
