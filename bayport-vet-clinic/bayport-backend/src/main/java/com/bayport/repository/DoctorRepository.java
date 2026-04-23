package com.bayport.repository;

import com.bayport.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    /**
     * Find a doctor by full name (case-insensitive).
     */
    @Query("SELECT d FROM Doctor d WHERE LOWER(d.fullName) = LOWER(:fullName)")
    Optional<Doctor> findByFullNameIgnoreCase(@Param("fullName") String fullName);
}

