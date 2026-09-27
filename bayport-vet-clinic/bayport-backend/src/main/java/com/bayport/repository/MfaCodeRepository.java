package com.bayport.repository;

import com.bayport.entity.MfaCode;
import com.bayport.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MfaCodeRepository extends JpaRepository<MfaCode, Long> {
    Optional<MfaCode> findTopByUserAndCodeAndUsedFalseOrderByExpiresAtDesc(User user, String code);
    Optional<MfaCode> findTopByEmailAndCodeAndUsedFalseOrderByExpiresAtDesc(String email, String code);
    void deleteByUser(User user);

    @Query("SELECT m FROM MfaCode m LEFT JOIN FETCH m.user u WHERE m.used = false AND u.id = :userId AND m.expiresAt > :now ORDER BY m.expiresAt DESC")
    List<MfaCode> findOpenForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Query("SELECT m FROM MfaCode m WHERE m.used = false AND m.user IS NULL AND LOWER(m.email) = LOWER(:email) AND m.expiresAt > :now ORDER BY m.expiresAt DESC")
    List<MfaCode> findOpenForEmail(@Param("email") String email, @Param("now") LocalDateTime now);
}
