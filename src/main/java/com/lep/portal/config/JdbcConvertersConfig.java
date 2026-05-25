package com.lep.portal.config;

import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;

import java.util.List;

/**
 * Global JDBC custom converters.
 * <p>
 * Covers all {@code citext} columns, enum mappings, and any
 * other PostgreSQL-specific types that need auto-conversion
 * when reading rows via Spring Data JDBC.
 */
@Configuration
public class JdbcConvertersConfig {

    @Bean
    public JdbcCustomConversions jdbcCustomConversions() {
        return new JdbcCustomConversions(List.of(new PgObjectToString()));
    }

    @ReadingConverter
    static class PgObjectToString implements Converter<PGobject, String> {
        @Override
        public String convert(PGobject src) {
            return src.getValue();
        }
    }
}
