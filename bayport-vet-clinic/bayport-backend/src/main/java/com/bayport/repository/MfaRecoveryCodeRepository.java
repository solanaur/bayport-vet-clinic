package com.bayport.repository;

import com.bayport.entity.MfaRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, Long> {
    List<MfaRecoveryCode> findByUserIdAndUsedAtIsNull(Long userId);
    void deleteByUserId(Long userId);
}
