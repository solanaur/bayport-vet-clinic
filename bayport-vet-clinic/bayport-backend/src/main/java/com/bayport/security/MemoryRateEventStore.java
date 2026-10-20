package com.bayport.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store used by unit tests. Production uses {@link JpaRateEventStore}.
 */
public class MemoryRateEventStore implements RateEventStore {

    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    @Override
    public long countSince(String bucketKey, long cutoffEpoch) {
        Deque<Long> q = windows.computeIfAbsent(bucketKey, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < cutoffEpoch) {
                q.pollFirst();
            }
            return q.size();
        }
    }

    @Override
    public void record(String bucketKey, long epoch) {
        Deque<Long> q = windows.computeIfAbsent(bucketKey, k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(epoch);
        }
    }

    @Override
    public void deleteOlderThan(String bucketKey, long cutoffEpoch) {
        countSince(bucketKey, cutoffEpoch);
    }
}
