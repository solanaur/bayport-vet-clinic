package com.bayport.security;

import com.bayport.entity.LoginLockout;
import com.bayport.repository.LoginLockoutRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Failed-login lockout stored per username and IP so it survives a restart.
 * An attacker on a different address cannot lock the clinic's own login.
 */
@Service
public class LoginAttemptService {

    private final int maxFailures;
    private final long lockSeconds;
    private final LoginLockoutRepository repository;

    public LoginAttemptService(
            @Value("${bayport.security.login.max-failures:5}") int maxFailures,
            @Value("${bayport.security.login.lock-seconds:900}") long lockSeconds,
            LoginLockoutRepository repository) {
        this.maxFailures = Math.max(3, maxFailures);
        this.lockSeconds = Math.max(60, lockSeconds);
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(String username, String ip) {
        return repository.findByLockKey(LoginLockoutPolicy.key(username, ip))
                .map(row -> LoginLockoutPolicy.blocked(row.getLockedUntilEpoch(), row.getNextAllowedEpoch(), now()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public long secondsRemaining(String username, String ip) {
        return repository.findByLockKey(LoginLockoutPolicy.key(username, ip))
                .map(row -> LoginLockoutPolicy.secondsRemaining(row.getLockedUntilEpoch(), row.getNextAllowedEpoch(), now()))
                .orElse(0L);
    }

    @Transactional
    public void recordFailure(String username, String ip) {
        String key = LoginLockoutPolicy.key(username, ip);
        LoginLockout row = repository.findByLockKey(key).orElseGet(() -> {
            LoginLockout created = new LoginLockout();
            created.setLockKey(key);
            return created;
        });
        LoginLockoutPolicy.Decision decision = LoginLockoutPolicy.onFailure(
                row.getFailureCount(),
                row.getLockedUntilEpoch(),
                now(),
                maxFailures,
                lockSeconds);
        row.setFailureCount(decision.failureCount());
        row.setLockedUntilEpoch(decision.lockedUntilEpoch());
        row.setNextAllowedEpoch(decision.nextAllowedEpoch());
        repository.save(row);
    }

    @Transactional
    public void recordSuccess(String username, String ip) {
        repository.deleteByLockKey(LoginLockoutPolicy.key(username, ip));
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }
}
