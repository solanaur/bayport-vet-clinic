package com.bayport.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {

    @Test
    void locksAfterConfiguredFailuresAndClearsOnSuccess() {
        LoginAttemptService svc = new LoginAttemptService(3, 60);
        assertFalse(svc.isBlocked("alice"));
        svc.recordFailure("alice");
        svc.recordFailure("alice");
        assertFalse(svc.isBlocked("alice"));
        svc.recordFailure("alice");
        assertTrue(svc.isBlocked("alice"));
        svc.recordSuccess("alice");
        assertFalse(svc.isBlocked("alice"));
    }
}
