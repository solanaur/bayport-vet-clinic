package com.bayport.web;

import com.bayport.entity.Reminder;
import com.bayport.entity.Owner;
import com.bayport.entity.Pet;
import com.bayport.repository.ReminderRepository;
import com.bayport.repository.OwnerRepository;
import com.bayport.repository.PetRepository;
import com.bayport.service.EmailTemplateService;
import com.bayport.service.EmailService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/reminder-email")
public class ReminderEmailController {

    private final ReminderRepository reminders;
    private final OwnerRepository owners;
    private final PetRepository pets;
    private final EmailService emailService;
    private final EmailTemplateService templateService;

    public ReminderEmailController(
            ReminderRepository reminders,
            OwnerRepository owners,
            PetRepository pets,
            EmailService emailService,
            EmailTemplateService templateService
    ) {
        this.reminders = reminders;
        this.owners = owners;
        this.pets = pets;
        this.emailService = emailService;
        this.templateService = templateService;
    }

    @PostMapping("/{id}")
    public String sendEmailForReminder(
            @PathVariable Long id,
            @RequestBody Map<String, String> request
    ) {
        Reminder r = reminders.findById(id).orElseThrow();
        Owner owner = r.getOwnerId() != null ? owners.findById(r.getOwnerId()).orElse(null) : null;
        Pet pet = r.getPetId() != null ? pets.findById(r.getPetId()).orElse(null) : null;

        String template = request.get("template");
        String subject = request.getOrDefault("subject", "Bayport Reminder");

        // GENERAL reminders without a specific ownerId -> broadcast to all owners with email
        if (r.getType() == com.bayport.entity.ReminderType.GENERAL && r.getOwnerId() == null) {
            // For general reminders, use the message as-is or apply template with only date placeholder
            String baseBody = r.getMessage();
            if (template != null && !template.isBlank()) {
                Map<String, String> generalValues = new HashMap<>();
                generalValues.put("date", r.getDate() != null ? r.getDate().toString() : "");
                // Don't replace {{ownerName}} or {{petName}} for general reminders - they don't apply
                baseBody = templateService.applyTemplate(template, generalValues);
            }
            final String bodyTemplate = baseBody;
            
            // Replace date placeholder in subject too
            String baseSubject = subject;
            if (r.getDate() != null) {
                baseSubject = baseSubject.replace("{{date}}", r.getDate().toString());
            }
            final String subjectTemplate = baseSubject;
            
            // Send to each owner individually, replacing {{ownerName}} per recipient
            owners.findAll().stream()
                    .filter(o -> o.getEmail() != null && !o.getEmail().trim().isEmpty())
                    .forEach(o -> {
                        String personalizedBody = bodyTemplate.replace("{{ownerName}}", o.getFullName());
                        String personalizedSubject = subjectTemplate.replace("{{ownerName}}", o.getFullName());
                        emailService.send(o.getEmail(), personalizedSubject, personalizedBody);
                    });
            r.setSent(true);
            r.setSentAt(LocalDateTime.now());
            r.setTargetEmail(null);
            reminders.save(r);
        } else {
            // Per-pet or targeted reminders
            Map<String, String> values = new HashMap<>();
            values.put("petName", pet != null ? pet.getName() : "");
            values.put("ownerName", owner != null ? owner.getFullName() : "");
            values.put("date", r.getDate() != null ? r.getDate().toString() : "");
            values.put("message", r.getMessage());
            values.put("followUpDate", r.getDate() != null ? r.getDate().toString() : "");
            values.put("dateStart", r.getDate() != null ? r.getDate().toString() : "");
            values.put("dateEnd", r.getDate() != null ? r.getDate().toString() : "");

            String body = template != null && !template.isBlank()
                    ? templateService.applyTemplate(template, values)
                    : r.getMessage();
            
            // Replace placeholders in subject
            String finalSubject = subject;
            for (String key : values.keySet()) {
                finalSubject = finalSubject.replace("{{" + key + "}}", values.get(key));
            }
            
            String destination = request.getOrDefault(
                    "to",
                    owner != null ? owner.getEmail() : r.getTargetEmail()
            );
            if (destination == null || destination.isBlank()) {
                throw new IllegalArgumentException("No recipient email specified for reminder");
            }
            emailService.send(destination, finalSubject, body);
            r.setSent(true);
            r.setSentAt(LocalDateTime.now());
            r.setTargetEmail(destination);
            reminders.save(r);
        }

        return "Email sent";
    }
}
