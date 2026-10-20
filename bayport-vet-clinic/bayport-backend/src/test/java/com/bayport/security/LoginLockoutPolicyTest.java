package com.bayport.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginLockoutPolicyTest {

    @Test
    void lockoutIsSeparatePerAddress() {
        assertNotEquals(LoginLockoutPolicy.key("admin", "10.0.0.8"), LoginLockoutPolicy.key("admin", "203.0.113.9"));
        assertEquals(LoginLockoutPolicy.key("Admin", "10.0.0.8"), LoginLockoutPolicy.key("admin", "10.0.0.8"));
    }

    @Test
    void delayGrowsUntilThePairIsLocked() {
        LoginLockoutPolicy.Decision first = LoginLockoutPolicy.onFailure(0, 0, 1_000, 5, 900);
        assertEquals(1, first.failureCount());
        assertEquals(0, first.lockedUntilEpoch());
        assertEquals(1_001, first.nextAllowedEpoch());
        assertTrue(LoginLockoutPolicy.blocked(first.lockedUntilEpoch(), first.nextAllowedEpoch(), 1_000));
        assertFalse(LoginLockoutPolicy.blocked(first.lockedUntilEpoch(), first.nextAllowedEpoch(), 1_001));

        LoginLockoutPolicy.Decision second = LoginLockoutPolicy.onFailure(first.failureCount(), 0, 1_010, 5, 900);
        assertEquals(1_012, second.nextAllowedEpoch());

        LoginLockoutPolicy.Decision locked = LoginLockoutPolicy.onFailure(4, 0, 2_000, 5, 900);
        assertEquals(2_900, locked.lockedUntilEpoch());
        assertEquals(900, LoginLockoutPolicy.secondsRemaining(locked.lockedUntilEpoch(), locked.nextAllowedEpoch(), 2_000));
    }
}
