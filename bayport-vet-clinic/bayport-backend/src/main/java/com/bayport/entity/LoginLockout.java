package com.bayport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "login_lockouts")
public class LoginLockout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lock_key", nullable = false, unique = true, length = 200)
    private String lockKey;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "locked_until_epoch", nullable = false)
    private long lockedUntilEpoch;

    @Column(name = "next_allowed_epoch", nullable = false)
    private long nextAllowedEpoch;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLockKey() { return lockKey; }
    public void setLockKey(String lockKey) { this.lockKey = lockKey; }

    public int getFailureCount() { return failureCount; }
    public void setFailureCount(int failureCount) { this.failureCount = failureCount; }

    public long getLockedUntilEpoch() { return lockedUntilEpoch; }
    public void setLockedUntilEpoch(long lockedUntilEpoch) { this.lockedUntilEpoch = lockedUntilEpoch; }

    public long getNextAllowedEpoch() { return nextAllowedEpoch; }
    public void setNextAllowedEpoch(long nextAllowedEpoch) { this.nextAllowedEpoch = nextAllowedEpoch; }
}
