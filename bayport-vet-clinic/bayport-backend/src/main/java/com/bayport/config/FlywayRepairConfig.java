package com.bayport.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 3.3 does not bind {@code spring.flyway.repair-on-migrate} (that key is ignored).
 * After a failed migration, Flyway validate fails until {@link Flyway#repair()} clears the failed row.
 * <p>
 * Enable with {@code bayport.flyway.repair-before-migrate=true} (default in {@code application.properties}).
 * Disable in production ({@code application-production.properties}) so checksum drift is not silently repaired.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Flyway.class)
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlywayRepairConfig {

    @Bean
    @ConditionalOnProperty(prefix = "bayport.flyway", name = "repair-before-migrate", havingValue = "true", matchIfMissing = true)
    public FlywayMigrationStrategy repairThenMigrate() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
