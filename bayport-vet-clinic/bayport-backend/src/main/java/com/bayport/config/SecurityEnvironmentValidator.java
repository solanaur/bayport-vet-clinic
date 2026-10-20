package com.bayport.config;

import com.bayport.security.SecretRequirements;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Fails startup with a clear message when a required secret is missing or too short.
 */
public class SecurityEnvironmentValidator implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Environment env = applicationContext.getEnvironment();
        SecretRequirements.requireAtLeast32Bytes(env.getProperty("jwt.secret", ""), "JWT_SECRET");
        SecretRequirements.requireAtLeast32Bytes(
                env.getProperty("bayport.security.totp-encryption-key", ""),
                "TOTP_ENCRYPTION_KEY");
        SecretRequirements.requireDatabasePassword(env.getProperty("spring.datasource.password", ""));
        if (env instanceof ConfigurableEnvironment configurable
                && configurable.acceptsProfiles(Profiles.of("production"))) {
            ProductionProfileRules.requireClinicDatabase(env.getProperty("spring.datasource.url", ""));
            ProductionProfileRules.requireCorsOrigins(env.getProperty("spring.web.cors.allowed-origins", ""));
            boolean ssl = Boolean.parseBoolean(env.getProperty("server.ssl.enabled", "false"));
            boolean allowLanHttp = Boolean.parseBoolean(env.getProperty("bayport.security.allow-lan-http", "false"));
            ProductionProfileRules.requireTransport(
                    ssl,
                    allowLanHttp,
                    env.getProperty("server.ssl.key-store", ""),
                    env.getProperty("server.ssl.key-store-password", ""));
        }
    }
}
