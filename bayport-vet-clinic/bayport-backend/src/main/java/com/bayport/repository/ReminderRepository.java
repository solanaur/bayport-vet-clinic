package com.bayport.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.bayport.entity.Reminder;
import com.bayport.entity.ReminderType;
import java.time.LocalDate;
import java.util.List;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {
    List<Reminder> findBySentFalseAndDate(LocalDate date);
    List<Reminder> findBySentFalseAndDateAndType(LocalDate date, ReminderType type);
    List<Reminder> findByPetId(Long petId);
}
