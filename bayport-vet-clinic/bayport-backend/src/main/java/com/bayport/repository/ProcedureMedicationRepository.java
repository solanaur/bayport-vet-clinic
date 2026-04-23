package com.bayport.repository;

import com.bayport.entity.ProcedureMedication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcedureMedicationRepository extends JpaRepository<ProcedureMedication, Long> {
    @Query("SELECT pm FROM ProcedureMedication pm WHERE pm.procedure.id = :procedureId")
    List<ProcedureMedication> findByProcedureId(@Param("procedureId") Long procedureId);

    void deleteByProcedure_Id(Long procedureId);
}

