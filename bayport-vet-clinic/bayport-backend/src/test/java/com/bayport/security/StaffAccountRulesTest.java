package com.bayport.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffAccountRulesTest {

    @Test
    void everyStaffRoleCanBeCreated() {
        assertTrue(StaffAccountRules.allowed("admin"));
        assertTrue(StaffAccountRules.allowed("Vet"));
        assertTrue(StaffAccountRules.allowed("veterinarian"));
        assertTrue(StaffAccountRules.allowed("front_office"));
        assertTrue(StaffAccountRules.allowed("receptionist"));
        assertTrue(StaffAccountRules.allowed("pharmacist"));
        assertFalse(StaffAccountRules.allowed("owner"));
        assertFalse(StaffAccountRules.allowed(""));
        assertFalse(StaffAccountRules.allowed(null));
    }

    @Test
    void rolesOpenTheMatchingScreens() {
        assertEquals("admin", StaffAccountRules.canonicalRole("Administrator"));
        assertEquals("vet", StaffAccountRules.canonicalRole("veterinarian"));
        assertEquals("front_office", StaffAccountRules.canonicalRole("pharmacist"));
        assertEquals("front_office", StaffAccountRules.canonicalRole("staff"));
    }

    @Test
    void creatingAStaffAccountDoesNotSignOutTheRealAdmin() {
        assertFalse(StaffAccountRules.endCurrentSession("R_Admin", List.of("admin", "vet")));
        assertTrue(StaffAccountRules.endCurrentSession("admin", List.of("admin")));
        assertFalse(StaffAccountRules.endCurrentSession("R_Admin", List.of()));
        assertFalse(StaffAccountRules.endCurrentSession(null, List.of("admin")));
    }
}
