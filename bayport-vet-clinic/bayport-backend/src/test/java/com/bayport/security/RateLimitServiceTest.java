package com.bayport.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitServiceTest {

    @Test
    void blocksExcessiveOtpSendsWithinWindow() {
        RateLimitService svc = new RateLimitService(20, 2, 10, 900, new MemoryRateEventStore());
        assertTrue(svc.allowOtpSend("user1"));
        assertTrue(svc.allowOtpSend("user1"));
        assertFalse(svc.allowOtpSend("user1"));
        assertTrue(svc.allowOtpSend("user2"));
    }
}
