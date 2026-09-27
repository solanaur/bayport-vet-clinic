package com.bayport.repository;

import com.bayport.entity.Procedure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProcedureRepository extends JpaRepository<Procedure, Long> {
    List<Procedure> findByPet_Id(Long petId);

    List<Procedure> findByConsultationIdOrderByIdAsc(Long consultationId);

    void deleteByPet_Id(Long petId);
}

