package com.bayport.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogScrubberTest {

    @Test
    void redactsPasswordsOtpsTokensAndTotpSecrets() {
        String scrubbed = LogScrubber.scrub(
                "password=clinic-secret otp: 123456 token=abc.def.ghi totpSecret=JBSWY3DPEHPK3PXP Authorization: Bearer eyJhbGciOi.payload.sig");
        assertFalse(scrubbed.contains("clinic-secret"));
        assertFalse(scrubbed.contains("123456"));
        assertFalse(scrubbed.contains("abc.def.ghi"));
        assertFalse(scrubbed.contains("JBSWY3DPEHPK3PXP"));
        assertFalse(scrubbed.contains("eyJhbGciOi"));
        assertFalse(scrubbed.toLowerCase().contains("bearer eyj"));
        assertTrue(scrubbed.contains("[REDACTED]"));
    }
}
