package com.bayport.service;

import com.bayport.entity.Reminder;
import com.bayport.entity.Owner;
import com.bayport.entity.ReminderType;
import com.bayport.repository.OwnerRepository;
import com.bayport.repository.ReminderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ReminderScheduler {

    @Autowired
    ReminderRepository reminderRepo;

    @Autowired
    OwnerRepository ownerRepo;

    @Autowired(required = false)
    EmailService emailService;

    @Scheduled(cron = "0 0 9 * * *")  // 9AM daily
    public void sendDueReminders() {
        log.info("Starting scheduled reminder email task for date: {}", LocalDate.now());
        
        List<Reminder> due = reminderRepo.findBySentFalseAndDateAndType(LocalDate.now(), ReminderType.PET);
        log.info("Found {} reminders due for today", due.size());

        if (emailService == null) {
            log.warn("EmailService not available. Reminder emails will not be sent. " +
                    "Configure SMTP settings in application.properties to enable email notifications.");
            return;
        }

        int sentCount = 0;
        int failedCount = 0;

        for (Reminder r : due) {
            try {
                Owner owner = ownerRepo.findById(r.getOwnerId()).orElse(null);
                if (owner == null) {
                    log.warn("Reminder {} has invalid ownerId: {}. Skipping.", r.getId(), r.getOwnerId());
                    continue;
                }
                
                if (owner.getEmail() == null || owner.getEmail().trim().isEmpty()) {
                    log.warn("Reminder {} for owner {} has no email address. Skipping.", r.getId(), owner.getFullName());
                    continue;
                }

                emailService.send(
                    owner.getEmail(),
                    "Pet Reminder - Bayport Veterinary Clinic",
                    r.getMessage()
                );
                
                r.setSent(true);
                r.setSentAt(LocalDateTime.now());
                reminderRepo.save(r);
                sentCount++;
                log.info("Reminder email sent successfully to: {} (Reminder ID: {})", owner.getEmail(), r.getId());
                
            } catch (Exception e) {
                failedCount++;
                log.error("Failed to send reminder email for reminder ID: {}. Error: {}", r.getId(), e.getMessage(), e);
                // Don't mark as sent if email failed - will retry next day
            }
        }
        
        log.info("Reminder email task completed. Sent: {}, Failed: {}", sentCount, failedCount);
    }
}
