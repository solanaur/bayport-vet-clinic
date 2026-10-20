package com.bayport.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretRequirementsTest {

    @Test
    void rejectsMissingAndShortSecrets() {
        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> SecretRequirements.requireAtLeast32Bytes(null, "JWT_SECRET"));
        assertTrue(missing.getMessage().contains("JWT_SECRET"));
        assertThrows(IllegalStateException.class,
                () -> SecretRequirements.requireAtLeast32Bytes("short-key", "JWT_SECRET"));
        assertEquals(32, SecretRequirements.requireAtLeast32Bytes("12345678901234567890123456789012", "JWT_SECRET").length);
    }

    @Test
    void rejectsWeakDatabasePasswords() {
        assertThrows(IllegalStateException.class, () -> SecretRequirements.requireDatabasePassword(""));
        assertThrows(IllegalStateException.class, () -> SecretRequirements.requireDatabasePassword("mypass"));
        assertThrows(IllegalStateException.class, () -> SecretRequirements.requireDatabasePassword("short-pass"));
        SecretRequirements.requireDatabasePassword("clinic-db-password");
    }
}
