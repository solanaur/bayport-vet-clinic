package com.bayport.repository;

import com.bayport.entity.BillingLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BillingLineRepository extends JpaRepository<BillingLine, Long> {
    List<BillingLine> findByConsultationIdOrderByLineOrderAscIdAsc(Long consultationId);

    @Query("SELECT l FROM BillingLine l JOIN FETCH l.billing b "
            + "WHERE b.issuedAt >= :from AND b.issuedAt < :to "
            + "ORDER BY b.id ASC, l.lineOrder ASC, l.id ASC")
    List<BillingLine> findIssuedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
