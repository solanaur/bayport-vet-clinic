package com.bayport.config;

import com.bayport.web.EmailTestController;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionProfileRulesTest {

    @Test
    void productionDatabaseMustBeMysql() {
        ProductionProfileRules.requireClinicDatabase("jdbc:mysql://127.0.0.1:3306/bayport_db");
        assertThrows(IllegalStateException.class,
                () -> ProductionProfileRules.requireClinicDatabase("jdbc:h2:file:./data/bayport-db"));
        assertThrows(IllegalStateException.class,
                () -> ProductionProfileRules.requireClinicDatabase("jdbc:postgresql://localhost:5432/bayport"));
        assertThrows(IllegalStateException.class,
                () -> ProductionProfileRules.requireClinicDatabase(""));
    }

    @Test
    void productionCorsRejectsWildcardsAndFileUrls() {
        ProductionProfileRules.requireCorsOrigins("https://clinic-pc:8443");
        assertThrows(IllegalStateException.class, () -> ProductionProfileRules.requireCorsOrigins(""));
        assertThrows(IllegalStateException.class, () -> ProductionProfileRules.requireCorsOrigins("*"));
        assertThrows(IllegalStateException.class, () -> ProductionProfileRules.requireCorsOrigins("https://*.example.com"));
        assertThrows(IllegalStateException.class, () -> ProductionProfileRules.requireCorsOrigins("file://"));
    }

    @Test
    void httpsIsRequiredUnlessPrivateLanIsExplicit() {
        ProductionProfileRules.requireTransport(true, false, "file:C:/Bayport/config/bayport.p12", "keystore-secret");
        ProductionProfileRules.requireTransport(false, true, "", "");
        assertThrows(IllegalStateException.class,
                () -> ProductionProfileRules.requireTransport(false, false, "", ""));
        assertThrows(IllegalStateException.class,
                () -> ProductionProfileRules.requireTransport(true, false, "", "secret"));
    }

    @Test
    void emptyDatabaseGetsBaselineAndExistingDatabaseDoesNot() {
        assertTrue(ClinicDatabaseBaseline.needsBaseline(false, false));
        assertFalse(ClinicDatabaseBaseline.needsBaseline(true, false));
        assertFalse(ClinicDatabaseBaseline.needsBaseline(true, true));
        assertFalse(ClinicDatabaseBaseline.needsBaseline(false, true));
        assertEquals("13", ClinicDatabaseBaseline.BASELINE_VERSION);
    }

    @Test
    void baselineScriptMatchesVersion13AndDoesNotSeedAccounts() throws Exception {
        String sql = new String(
                getClass().getClassLoader().getResourceAsStream("db/baseline/mysql-clinic-schema.sql").readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(sql.contains("flyway-baseline-version: 13"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS users"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS pets"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS billing_records"));
        assertTrue(sql.contains("totp_secret VARCHAR(512)"));
        assertFalse(sql.toLowerCase().contains("insert into"));
        assertFalse(sql.contains("display_password"));
    }

    @Test
    void productionPropertiesValidateSchemaAndKeepFlyway() throws Exception {
        String text = new String(
                getClass().getClassLoader().getResourceAsStream("application-production.properties").readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(text.contains("spring.jpa.hibernate.ddl-auto=validate"));
        assertTrue(text.contains("spring.flyway.enabled=true"));
        assertFalse(text.contains("ddl-auto=update"));
        assertFalse(text.contains("file://"));
    }

    @Test
    void emailTestEndpointIsAbsentInProduction() {
        Profile profile = EmailTestController.class.getAnnotation(Profile.class);
        assertNotNull(profile);
        assertEquals("!production", profile.value()[0]);
    }
}
