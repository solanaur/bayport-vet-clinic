package com.bayport.repository;

import com.bayport.entity.LoginLockout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoginLockoutRepository extends JpaRepository<LoginLockout, Long> {
    Optional<LoginLockout> findByLockKey(String lockKey);
    void deleteByLockKey(String lockKey);
}
