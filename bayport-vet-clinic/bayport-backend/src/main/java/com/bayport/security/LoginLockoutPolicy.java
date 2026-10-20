package com.bayport.security;

/**
 * Lockout state for one username + IP pair. A failure from another address does not lock this pair.
 */
public final class LoginLockoutPolicy {

    public record Decision(int failureCount, long lockedUntilEpoch, long nextAllowedEpoch) {}

    private LoginLockoutPolicy() {}

    public static String key(String username, String ip) {
        String name = username == null ? "" : username.trim().toLowerCase();
        String address = (ip == null || ip.isBlank()) ? "unknown" : ip.trim();
        return name + "|" + address;
    }

    public static Decision onFailure(int failureCount, long lockedUntilEpoch, long nowEpoch, int maxFailures, long lockSeconds) {
        int current = failureCount;
        if (lockedUntilEpoch > 0 && nowEpoch >= lockedUntilEpoch) {
            current = 0;
        }
        int next = current + 1;
        int limit = Math.max(3, maxFailures);
        if (next >= limit) {
            long until = nowEpoch + Math.max(60, lockSeconds);
            return new Decision(next, until, until);
        }
        long delay = 1L << Math.min(next - 1, 5);
        return new Decision(next, 0L, nowEpoch + delay);
    }

    public static boolean blocked(long lockedUntilEpoch, long nextAllowedEpoch, long nowEpoch) {
        return nowEpoch < lockedUntilEpoch || nowEpoch < nextAllowedEpoch;
    }

    public static long secondsRemaining(long lockedUntilEpoch, long nextAllowedEpoch, long nowEpoch) {
        long until = Math.max(lockedUntilEpoch, nextAllowedEpoch);
        return Math.max(0L, until - nowEpoch);
    }
}
