package com.bayport.security;

import com.bayport.entity.AuthRateEvent;
import com.bayport.repository.AuthRateEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaRateEventStore implements RateEventStore {

    private final AuthRateEventRepository repository;

    public JpaRateEventStore(AuthRateEventRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public long countSince(String bucketKey, long cutoffEpoch) {
        return repository.countSince(bucketKey, cutoffEpoch);
    }

    @Override
    @Transactional
    public void record(String bucketKey, long epoch) {
        AuthRateEvent event = new AuthRateEvent();
        event.setBucketKey(bucketKey);
        event.setEventEpoch(epoch);
        repository.save(event);
    }

    @Override
    @Transactional
    public void deleteOlderThan(String bucketKey, long cutoffEpoch) {
        repository.deleteOlder(bucketKey, cutoffEpoch);
    }
}
