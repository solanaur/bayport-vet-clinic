package com.bayport.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporaryAdminRulesTest {

    @Test
    void temporaryLoginIsOnlyTheFactoryAdmin() {
        assertTrue(TemporaryAdminRules.isTemporaryLogin("admin", "admin123"));
        assertTrue(TemporaryAdminRules.isTemporaryLogin(" Admin ", "admin123"));
        assertFalse(TemporaryAdminRules.isTemporaryLogin("admin", "a-real-password"));
        assertFalse(TemporaryAdminRules.isTemporaryLogin("clinic", "admin123"));
    }

    @Test
    void durableAdminRequiresAnAdminRoleAndANonFactoryPassword() {
        assertTrue(TemporaryAdminRules.isDurableAdmin("admin", false));
        assertTrue(TemporaryAdminRules.isDurableAdmin("Administrator", false));
        assertFalse(TemporaryAdminRules.isDurableAdmin("admin", true));
        assertFalse(TemporaryAdminRules.isDurableAdmin("vet", false));
        assertFalse(TemporaryAdminRules.isDurableAdmin(null, false));
    }
}
