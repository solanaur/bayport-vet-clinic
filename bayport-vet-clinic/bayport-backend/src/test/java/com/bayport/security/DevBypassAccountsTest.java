package com.bayport.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevBypassAccountsTest {

    @Test
    void onlyTheThreeLocalPasswordsMatch() {
        assertTrue(DevBypassAccounts.matches("admin", "admin123"));
        assertTrue(DevBypassAccounts.matches("Vet", "vet123"));
        assertTrue(DevBypassAccounts.matches(" front ", "front123"));
        assertFalse(DevBypassAccounts.matches("admin", "vet123"));
        assertFalse(DevBypassAccounts.matches("frontdesk", "frontdesk123"));
        assertFalse(DevBypassAccounts.matches("front", "front1234"));
    }
}
