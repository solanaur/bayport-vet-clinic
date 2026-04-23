package com.bayport.repository;

import com.bayport.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    
    /**
     * Find appointments assigned to a specific veterinarian.
     */
    @Query("SELECT a FROM Appointment a WHERE a.vet = :vetName")
    List<Appointment> findByVet(@Param("vetName") String vetName);
    
    /**
     * Check for overlapping appointments for a vet on a specific date and time.
     * Assumes 30-minute appointment duration.
     */
    @Query("SELECT a FROM Appointment a WHERE a.vet = :vetName " +
           "AND a.date = :date " +
           "AND a.status != 'Cancelled' " +
           "AND a.time = :time")
    List<Appointment> findOverlappingAppointments(@Param("vetName") String vetName,
                                                  @Param("date") LocalDate date,
                                                  @Param("time") String time);

    /**
     * Counts how many active appointments (any vet) already occupy the same
     * date/time slot. Used to enforce the "5 patients per 30-minute block"
     * rule at the clinic level.
     */
    @Query("SELECT COUNT(a) FROM Appointment a " +
           "WHERE a.date = :date " +
           "AND a.time = :time " +
           "AND (a.status IS NULL OR a.status <> 'Cancelled')")
    long countActiveAppointments(@Param("date") LocalDate date,
                                 @Param("time") String time);
    
    /**
     * Find appointments by vet and date range.
     */
    @Query("SELECT a FROM Appointment a WHERE a.vet = :vetName " +
           "AND a.date BETWEEN :startDate AND :endDate")
    List<Appointment> findByVetAndDateRange(@Param("vetName") String vetName,
                                              @Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);

    List<Appointment> findByPetId(Long petId);

    void deleteByPetId(Long petId);

    List<Appointment> findByOwner(String owner);

    /**
     * Find appointments by date.
     */
    @Query("SELECT a FROM Appointment a WHERE a.date = :date ORDER BY a.time")
    List<Appointment> findByDate(@Param("date") LocalDate date);
}

