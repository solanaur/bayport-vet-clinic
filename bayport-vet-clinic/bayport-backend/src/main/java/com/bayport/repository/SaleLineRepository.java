package com.bayport.repository;

import com.bayport.entity.SaleLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SaleLineRepository extends JpaRepository<SaleLine, Long> {

    List<SaleLine> findBySale_IdOrderByIdAsc(Long saleId);

    long countBySale_Id(Long saleId);

    @Query("SELECT sl FROM SaleLine sl JOIN FETCH sl.sale s WHERE LOWER(TRIM(COALESCE(s.source,''))) = 'pos' AND sl.lineKind = :kind "
            + "AND s.occurredAt >= :start AND s.occurredAt < :endExclusive ORDER BY s.occurredAt DESC, sl.id ASC")
    List<SaleLine> findPosLinesByKindBetween(
            @Param("kind") String kind,
            @Param("start") LocalDateTime start,
            @Param("endExclusive") LocalDateTime endExclusive);
}
