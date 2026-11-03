package com.bayport.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActivityLogRulesTest {

    @Test
    void authenticationAndSecurityAreSeparated() {
        assertEquals(ActivityLogRules.AUTHENTICATION,
                ActivityLogRules.categorizeAudit("LOGIN_SUCCESS", "User"));
        assertEquals(ActivityLogRules.AUTHENTICATION,
                ActivityLogRules.categorizeAudit("LOGOUT", "User"));
        assertEquals(ActivityLogRules.AUTHENTICATION,
                ActivityLogRules.categorizeAudit("LOGIN_FAILED", "User"));
        assertEquals(ActivityLogRules.SECURITY,
                ActivityLogRules.categorizeAudit("OTP_FAILED", "User"));
        assertEquals(ActivityLogRules.SECURITY,
                ActivityLogRules.categorizeAudit("TOKEN_REJECTED", "Auth"));
        assertEquals(ActivityLogRules.SECURITY,
                ActivityLogRules.categorizeAudit("UNAUTHORIZED_RECORD_ACCESS_ATTEMPT", "Pet"));
    }

    @Test
    void moduleCategoriesPreferEntityForGenericActions() {
        assertEquals(ActivityLogRules.PETS,
                ActivityLogRules.categorizeAudit("SOFT_DELETE", "Pet"));
        assertEquals(ActivityLogRules.APPOINTMENTS,
                ActivityLogRules.categorizeAudit("HARD_DELETE", "Appointment"));
        assertEquals(ActivityLogRules.BILLING,
                ActivityLogRules.categorizeAudit("VOID", "Invoice"));
        assertEquals(ActivityLogRules.USERS_ROLES,
                ActivityLogRules.categorizeAudit("ROLE_CHANGED", "User"));
        assertEquals(ActivityLogRules.BACKUPS,
                ActivityLogRules.categorizeAudit("BACKUP_CREATED", "Backup"));
        assertEquals(ActivityLogRules.SYSTEM,
                ActivityLogRules.categorizeAudit("IMPORT", "Record"));
        assertEquals(ActivityLogRules.SYSTEM,
                ActivityLogRules.categorizeAudit("CREATE", "UnknownThing"));
    }

    @Test
    void operationAndDataAccessCategories() {
        assertEquals(ActivityLogRules.PETS,
                ActivityLogRules.categorizeOperation("PET_CREATED"));
        assertEquals(ActivityLogRules.APPOINTMENTS,
                ActivityLogRules.categorizeOperation("APPT_CANCELLED"));
        assertEquals(ActivityLogRules.PRESCRIPTIONS,
                ActivityLogRules.categorizeOperation("RX_CREATED"));
        assertEquals(ActivityLogRules.PETS,
                ActivityLogRules.categorizeDataAccess("Pet", "READ"));
        assertEquals(ActivityLogRules.SYSTEM,
                ActivityLogRules.categorizeDataAccess("Report", "EXPORT"));
    }
}
