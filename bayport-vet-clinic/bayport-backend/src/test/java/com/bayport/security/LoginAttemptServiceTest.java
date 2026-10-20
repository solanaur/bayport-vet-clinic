package com.bayport.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lockout behavior lives in {@link LoginLockoutPolicy}. This checks the service still builds with a store.
 */
class LoginAttemptServiceTest {

    @Test
    void policyClearsAfterTheLockWindow() {
        LoginLockoutPolicy.Decision locked = LoginLockoutPolicy.onFailure(4, 0, 100, 5, 60);
        assertTrue(LoginLockoutPolicy.blocked(locked.lockedUntilEpoch(), locked.nextAllowedEpoch(), 100));
        assertFalse(LoginLockoutPolicy.blocked(locked.lockedUntilEpoch(), locked.nextAllowedEpoch(), 160));
    }
}
