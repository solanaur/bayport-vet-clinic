package com.bayport.security;

/**
 * Sliding-window counters for login and OTP limits. The database implementation survives restarts.
 */
public interface RateEventStore {
    long countSince(String bucketKey, long cutoffEpoch);

    void record(String bucketKey, long epoch);

    void deleteOlderThan(String bucketKey, long cutoffEpoch);
}
