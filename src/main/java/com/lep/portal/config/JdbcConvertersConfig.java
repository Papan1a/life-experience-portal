package com.lep.portal.config;

import java.sql.SQLException;
import java.util.List;

import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.lang.NonNull;

import com.lep.portal.experience.ExperienceStatus;

/**
 * Global JDBC custom converters.
 * <p>
 * Covers all {@code citext} columns, enum mappings, and any
 * other PostgreSQL-specific types that need auto-conversion
 * when reading/writing rows via Spring Data JDBC.
 */
@Configuration
public class JdbcConvertersConfig {

    @Bean
    public JdbcCustomConversions jdbcCustomConversions() {
        return new JdbcCustomConversions(List.of(
                new PgObjectToString(),
                new ExperienceStatusToPgObject(),
                new PgObjectToExperienceStatus()
        ));
    }

    @ReadingConverter
    static class PgObjectToString implements Converter<PGobject, String> {
        @Override
        @NonNull
        public String convert(@NonNull PGobject src) {
            return src.getValue();
        }
    }

    /**
     * Converts {@link ExperienceStatus} Java enum to PostgreSQL
     * {@code experience_status} typed value via {@link PGobject}.
     */
    @WritingConverter
    static class ExperienceStatusToPgObject implements Converter<ExperienceStatus, PGobject> {
        @Override
        @NonNull
        public PGobject convert(@NonNull ExperienceStatus source) {
            PGobject obj = new PGobject();
            obj.setType("experience_status");
            try {
                obj.setValue(source.name());
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to convert ExperienceStatus to PGobject", e);
            }
            return obj;
        }
    }

    /**
     * Converts PostgreSQL {@code experience_status} value back to
     * {@link ExperienceStatus} Java enum.
     */
    @ReadingConverter
    static class PgObjectToExperienceStatus implements Converter<PGobject, ExperienceStatus> {
        @Override
        @NonNull
        public ExperienceStatus convert(@NonNull PGobject source) {
            return ExperienceStatus.valueOf(source.getValue());
        }
    }
}
