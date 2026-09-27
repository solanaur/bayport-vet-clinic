package com.bayport.repository;

import com.bayport.entity.Consultation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsultationRepository extends JpaRepository<Consultation, Long> {
    List<Consultation> findByPetIdOrderByConsultDateDescIdDesc(Long petId);
}
