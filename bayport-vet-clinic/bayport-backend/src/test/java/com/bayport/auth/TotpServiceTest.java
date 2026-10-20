package com.bayport.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpServiceTest {

    @Test
    void matchingStepRejectsOlderCodes() {
        TotpService totp = new TotpService();
        String secret = totp.generateSecret();
        long step = totp.currentStep();
        String current = totp.codeForStep(secret, step);
        assertEquals(step, totp.matchingStep(secret, current));
        assertTrue(TotpService.isReplay(step, step));
        assertTrue(TotpService.isReplay(step, step - 1));
        assertTrue(!TotpService.isReplay(step, step + 1));
        assertNull(totp.matchingStep(secret, "000000".equals(current) ? "111111" : "000000"));
    }
}
