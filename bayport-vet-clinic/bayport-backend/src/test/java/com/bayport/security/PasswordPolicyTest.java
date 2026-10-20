package com.bayport.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @Test
    void factoryPasswordsAreRecognizedRegardlessOfCase() {
        assertTrue(PasswordPolicy.isFactoryDefault("admin123"));
        assertTrue(PasswordPolicy.isFactoryDefault("Vet123"));
        assertTrue(PasswordPolicy.isFactoryDefault("frontdesk123"));
        assertFalse(PasswordPolicy.isFactoryDefault("clinic-door-19"));
    }

    @Test
    void rejectsShortAndFactoryPasswords() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.require("short"));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.require("admin123"));
        assertDoesNotThrow(() -> PasswordPolicy.require("clinic-door-19"));
    }
}
