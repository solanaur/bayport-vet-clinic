package com.bayport.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Sliding-window rate limiter for auth endpoints. Counts are stored in the database.
 */
@Service
public class RateLimitService {

    private final int loginMaxPerWindow;
    private final int otpSendMaxPerWindow;
    private final int otpVerifyMaxPerWindow;
    private final long windowSeconds;
    private final RateEventStore store;

    public RateLimitService(
            @Value("${bayport.security.rate-limit.login-max:20}") int loginMaxPerWindow,
            @Value("${bayport.security.rate-limit.otp-send-max:5}") int otpSendMaxPerWindow,
            @Value("${bayport.security.rate-limit.otp-verify-max:10}") int otpVerifyMaxPerWindow,
            @Value("${bayport.security.rate-limit.window-seconds:900}") long windowSeconds,
            RateEventStore store) {
        this.loginMaxPerWindow = Math.max(3, loginMaxPerWindow);
        this.otpSendMaxPerWindow = Math.max(2, otpSendMaxPerWindow);
        this.otpVerifyMaxPerWindow = Math.max(3, otpVerifyMaxPerWindow);
        this.windowSeconds = Math.max(60, windowSeconds);
        this.store = store;
    }

    @Transactional
    public boolean allowLogin(String key) {
        return allow("login:" + normalize(key), loginMaxPerWindow);
    }

    @Transactional
    public boolean allowOtpSend(String key) {
        return allow("otp-send:" + normalize(key), otpSendMaxPerWindow);
    }

    @Transactional
    public boolean allowOtpVerify(String key) {
        return allow("otp-verify:" + normalize(key), otpVerifyMaxPerWindow);
    }

    @Transactional
    public boolean allowPasswordReset(String key) {
        return allow("pw-reset:" + normalize(key), otpSendMaxPerWindow);
    }

    private boolean allow(String bucketKey, int max) {
        long now = Instant.now().getEpochSecond();
        long cutoff = now - windowSeconds;
        store.deleteOlderThan(bucketKey, cutoff);
        if (store.countSince(bucketKey, cutoff) >= max) {
            return false;
        }
        store.record(bucketKey, now);
        return true;
    }

    private static String normalize(String key) {
        return key == null ? "unknown" : key.trim().toLowerCase();
    }
}
