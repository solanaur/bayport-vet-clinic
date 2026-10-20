package com.bayport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_rate_events", indexes = {
        @Index(name = "idx_auth_rate_bucket", columnList = "bucket_key, event_epoch")
})
public class AuthRateEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bucket_key", nullable = false, length = 200)
    private String bucketKey;

    @Column(name = "event_epoch", nullable = false)
    private long eventEpoch;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBucketKey() { return bucketKey; }
    public void setBucketKey(String bucketKey) { this.bucketKey = bucketKey; }

    public long getEventEpoch() { return eventEpoch; }
    public void setEventEpoch(long eventEpoch) { this.eventEpoch = eventEpoch; }
}
