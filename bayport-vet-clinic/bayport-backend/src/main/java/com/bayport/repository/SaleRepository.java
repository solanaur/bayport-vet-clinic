package com.bayport.repository;

import com.bayport.entity.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @Query("SELECT CAST(s.occurredAt AS date), SUM(s.amount) FROM Sale s " +
           "WHERE s.occurredAt BETWEEN :start AND :end " +
           "GROUP BY CAST(s.occurredAt AS date) ORDER BY CAST(s.occurredAt AS date)")
    List<Object[]> sumTotalsBetween(@Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(s.amount), 0) FROM Sale s WHERE LOWER(TRIM(COALESCE(s.source,''))) = LOWER(TRIM(:source)) "
           + "AND s.occurredAt >= :start AND s.occurredAt < :endExclusive")
    java.math.BigDecimal sumAmountBySourceBetween(@Param("source") String source,
                                                   @Param("start") LocalDateTime start,
                                                   @Param("endExclusive") LocalDateTime endExclusive);

    /** POS checkouts (case-insensitive source) newest first. */
    @Query("SELECT s FROM Sale s WHERE LOWER(TRIM(COALESCE(s.source,''))) = 'pos' ORDER BY s.occurredAt DESC")
    Page<Sale> findRecentPosPage(Pageable pageable);

    @Query("SELECT s FROM Sale s WHERE LOWER(TRIM(COALESCE(s.source,''))) = 'pos' AND NOT EXISTS "
            + "(SELECT 1 FROM SaleLine sl WHERE sl.sale.id = s.id)")
    List<Sale> findPosSalesWithNoLines();

    @Query("SELECT s FROM Sale s WHERE LOWER(TRIM(COALESCE(s.source,''))) = 'pos' "
            + "AND s.occurredAt >= :start AND s.occurredAt < :endExclusive ORDER BY s.occurredAt DESC")
    List<Sale> findPosSalesOccurredBetween(@Param("start") LocalDateTime start,
                                           @Param("endExclusive") LocalDateTime endExclusive);
}

