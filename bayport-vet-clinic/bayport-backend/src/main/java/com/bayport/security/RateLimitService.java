package com.bayport.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory sliding-window rate limiter for auth-sensitive endpoints.
 */
@Service
public class RateLimitService {

    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    private final int loginMaxPerWindow;
    private final int otpSendMaxPerWindow;
    private final int otpVerifyMaxPerWindow;
    private final long windowSeconds;

    public RateLimitService(
            @Value("${bayport.security.rate-limit.login-max:20}") int loginMaxPerWindow,
            @Value("${bayport.security.rate-limit.otp-send-max:5}") int otpSendMaxPerWindow,
            @Value("${bayport.security.rate-limit.otp-verify-max:10}") int otpVerifyMaxPerWindow,
            @Value("${bayport.security.rate-limit.window-seconds:900}") long windowSeconds) {
        this.loginMaxPerWindow = Math.max(3, loginMaxPerWindow);
        this.otpSendMaxPerWindow = Math.max(2, otpSendMaxPerWindow);
        this.otpVerifyMaxPerWindow = Math.max(3, otpVerifyMaxPerWindow);
        this.windowSeconds = Math.max(60, windowSeconds);
    }

    public boolean allowLogin(String key) {
        return allow("login:" + normalize(key), loginMaxPerWindow);
    }

    public boolean allowOtpSend(String key) {
        return allow("otp-send:" + normalize(key), otpSendMaxPerWindow);
    }

    public boolean allowOtpVerify(String key) {
        return allow("otp-verify:" + normalize(key), otpVerifyMaxPerWindow);
    }

    public boolean allowPasswordReset(String key) {
        return allow("pw-reset:" + normalize(key), otpSendMaxPerWindow);
    }

    private boolean allow(String bucketKey, int max) {
        long now = Instant.now().getEpochSecond();
        long cutoff = now - windowSeconds;
        Deque<Long> q = windows.computeIfAbsent(bucketKey, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < cutoff) {
                q.pollFirst();
            }
            if (q.size() >= max) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }

    private static String normalize(String key) {
        return key == null ? "unknown" : key.trim().toLowerCase();
    }
}
