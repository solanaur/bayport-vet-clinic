package com.bayport.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginGateTest {

    @Test
    void factoryPasswordForcesAChangeEvenWithoutAStoredFlag() {
        assertTrue(LoginGate.mustReplacePassword("admin123", false));
        assertTrue(LoginGate.mustReplacePassword("clinic-door-19", true));
        assertFalse(LoginGate.mustReplacePassword("clinic-door-19", false));
    }
}
