package com.bayport.repository;

import com.bayport.entity.MfaCode;
import com.bayport.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MfaCodeRepository extends JpaRepository<MfaCode, Long> {
    Optional<MfaCode> findTopByUserAndCodeAndUsedFalseOrderByExpiresAtDesc(User user, String code);
    Optional<MfaCode> findTopByEmailAndCodeAndUsedFalseOrderByExpiresAtDesc(String email, String code);
    void deleteByUser(User user);
}
