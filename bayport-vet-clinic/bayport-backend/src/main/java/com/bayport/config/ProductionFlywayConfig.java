package com.bayport.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * Production schema changes come from Flyway. An empty database gets the checked-in baseline first.
 */
@Configuration(proxyBeanMethods = false)
@Profile("production")
@ConditionalOnClass(Flyway.class)
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true")
public class ProductionFlywayConfig {

    @Bean
    @ConditionalOnProperty(prefix = "bayport.flyway", name = "repair-before-migrate", havingValue = "false")
    public FlywayMigrationStrategy clinicBaselineThenMigrate(DataSource dataSource) {
        return flyway -> {
            try {
                ClinicDatabaseBaseline.prepareEmptyDatabase(dataSource);
            } catch (Exception ex) {
                throw new IllegalStateException("Could not prepare the empty clinic database.", ex);
            }
            flyway.migrate();
        };
    }
}
