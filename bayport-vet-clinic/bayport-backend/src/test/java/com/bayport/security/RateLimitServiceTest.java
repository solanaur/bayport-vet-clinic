package com.bayport.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitServiceTest {

    @Test
    void blocksExcessiveOtpSendsWithinWindow() {
        RateLimitService svc = new RateLimitService(20, 2, 10, 900);
        assertTrue(svc.allowOtpSend("user1"));
        assertTrue(svc.allowOtpSend("user1"));
        assertFalse(svc.allowOtpSend("user1"));
        assertTrue(svc.allowOtpSend("user2"));
    }
}
