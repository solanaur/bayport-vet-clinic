package com.bayport.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FixedAdminAccountTest {

    @Test
    void existingAccountIsLeftAlone() {
        assertEquals(FixedAdminAccount.Plan.KEEP, FixedAdminAccount.plan(true, "some-local-password"));
        assertEquals(FixedAdminAccount.Plan.KEEP, FixedAdminAccount.plan(true, ""));
        assertEquals(FixedAdminAccount.Plan.KEEP, FixedAdminAccount.plan(true, null));
    }

    @Test
    void missingAccountIsCreatedOnlyWhenALocalPasswordIsSet() {
        assertEquals(FixedAdminAccount.Plan.CREATE, FixedAdminAccount.plan(false, "some-local-password"));
        assertEquals(FixedAdminAccount.Plan.MISSING_PASSWORD, FixedAdminAccount.plan(false, ""));
        assertEquals(FixedAdminAccount.Plan.MISSING_PASSWORD, FixedAdminAccount.plan(false, "   "));
        assertEquals(FixedAdminAccount.Plan.MISSING_PASSWORD, FixedAdminAccount.plan(false, null));
    }

    @Test
    void usernameIsTheClinicAdministrator() {
        assertEquals("R_Admin", FixedAdminAccount.USERNAME);
    }
}
