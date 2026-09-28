package com.cbo.players.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Hibernate creates the new tables/columns, but does not relax constraints or
 * widen existing varchar columns in this PostgreSQL schema.
 */
@Configuration(proxyBeanMethods = false)
public class CatalogSchemaCompatibility {
    @Bean
    @DependsOn("entityManagerFactory")
    public InitializingBean initializeCatalogSchema(JdbcTemplate jdbc) {
        return () -> {
            Boolean postgres = jdbc.execute((ConnectionCallback<Boolean>) connection ->
                    "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()));
            if (Boolean.TRUE.equals(postgres)) {
                jdbc.execute("""
                        ALTER TABLE players
                        ALTER COLUMN first_name TYPE varchar(200),
                        ALTER COLUMN last_name TYPE varchar(200),
                        ALTER COLUMN nationality DROP NOT NULL,
                        ALTER COLUMN position DROP NOT NULL
                        """);
            }
        };
    }
}
