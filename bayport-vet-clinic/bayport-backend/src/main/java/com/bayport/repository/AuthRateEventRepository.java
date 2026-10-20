package com.bayport.repository;

import com.bayport.entity.AuthRateEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthRateEventRepository extends JpaRepository<AuthRateEvent, Long> {

    @Query("select count(e) from AuthRateEvent e where e.bucketKey = :key and e.eventEpoch >= :cutoff")
    long countSince(@Param("key") String key, @Param("cutoff") long cutoff);

    @Modifying
    @Query("delete from AuthRateEvent e where e.bucketKey = :key and e.eventEpoch < :cutoff")
    int deleteOlder(@Param("key") String key, @Param("cutoff") long cutoff);
}
