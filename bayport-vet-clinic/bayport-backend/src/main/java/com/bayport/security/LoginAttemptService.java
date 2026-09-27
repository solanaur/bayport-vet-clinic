package com.bayport.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Temporary lockout after repeated failed logins. Locks expire automatically (no permanent lockouts).
 */
@Service
public class LoginAttemptService {

    private final int maxFailures;
    private final long lockSeconds;

    private final Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();
    private final Map<String, Long> lockedUntil = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${bayport.security.login.max-failures:5}") int maxFailures,
            @Value("${bayport.security.login.lock-seconds:900}") long lockSeconds) {
        this.maxFailures = Math.max(3, maxFailures);
        this.lockSeconds = Math.max(60, lockSeconds);
    }

    public boolean isBlocked(String username) {
        String key = normalize(username);
        Long until = lockedUntil.get(key);
        if (until == null) {
            return false;
        }
        long now = Instant.now().getEpochSecond();
        if (now >= until) {
            lockedUntil.remove(key);
            failures.remove(key);
            return false;
        }
        return true;
    }

    public long secondsRemaining(String username) {
        Long until = lockedUntil.get(normalize(username));
        if (until == null) {
            return 0;
        }
        return Math.max(0, until - Instant.now().getEpochSecond());
    }

    public void recordFailure(String username) {
        String key = normalize(username);
        int count = failures.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        if (count >= maxFailures) {
            lockedUntil.put(key, Instant.now().getEpochSecond() + lockSeconds);
            failures.put(key, new AtomicInteger(0));
        }
    }

    public void recordSuccess(String username) {
        String key = normalize(username);
        failures.remove(key);
        lockedUntil.remove(key);
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
