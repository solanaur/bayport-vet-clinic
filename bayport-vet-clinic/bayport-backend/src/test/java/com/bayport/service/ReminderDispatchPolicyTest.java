package com.bayport.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReminderDispatchPolicyTest {

    @Test
    void skipsWhenOwnerHasNeitherEmailNorPhone() {
        ReminderDispatchPolicy.Plan plan = ReminderDispatchPolicy.plan(null, "  ", true, true);
        assertFalse(plan.hasContact());
        assertFalse(plan.sendEmail());
        assertFalse(plan.sendSms());
    }

    @Test
    void sendsSmsWhenPhoneExistsAndSmsIsConfigured() {
        ReminderDispatchPolicy.Plan plan = ReminderDispatchPolicy.plan("", "09171234567", false, true);
        assertTrue(plan.hasContact());
        assertFalse(plan.sendEmail());
        assertTrue(plan.sendSms());
    }

    @Test
    void doesNotSendEmailWhenMailIsNotConfigured() {
        ReminderDispatchPolicy.Plan plan = ReminderDispatchPolicy.plan("owner@clinic.test", null, false, false);
        assertTrue(plan.hasContact());
        assertFalse(plan.sendEmail());
        assertFalse(plan.sendSms());
    }
}
