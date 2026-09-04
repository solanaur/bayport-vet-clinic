package com.bayport.web;

import com.bayport.dto.ReminderDTO;
import com.bayport.entity.Owner;
import com.bayport.entity.Pet;
import com.bayport.entity.Reminder;
import com.bayport.entity.ReminderType;
import com.bayport.repository.OwnerRepository;
import com.bayport.repository.PetRepository;
import com.bayport.repository.ReminderRepository;
import com.bayport.service.ReminderScheduler;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    private final ReminderRepository repo;
    private final PetRepository pets;
    private final OwnerRepository owners;
    private final ReminderScheduler reminderScheduler;

    public ReminderController(
            ReminderRepository repo,
            PetRepository pets,
            OwnerRepository owners,
            ReminderScheduler reminderScheduler) {
        this.repo = repo;
        this.pets = pets;
        this.owners = owners;
        this.reminderScheduler = reminderScheduler;
    }

    // Convert Reminder → ReminderDTO
    private ReminderDTO map(Reminder r) {
        Pet pet = r.getPetId() != null ? pets.findById(r.getPetId()).orElse(null) : null;
        Owner owner = r.getOwnerId() != null ? owners.findById(r.getOwnerId()).orElse(null) : null;

        if (owner == null && pet != null && pet.getOwnerId() != null) {
            owner = owners.findById(pet.getOwnerId()).orElse(null);
        }

        String petName = pet != null && pet.getName() != null && !pet.getName().isBlank()
                ? pet.getName().trim()
                : null;
        String ownerName = owner != null && owner.getFullName() != null && !owner.getFullName().isBlank()
                ? owner.getFullName().trim()
                : null;
        if (ownerName == null && pet != null && pet.getOwner() != null && !pet.getOwner().isBlank()) {
            ownerName = pet.getOwner().trim();
        }

        String ownerEmail = owner != null ? owner.getEmail() : null;
        return ReminderDTO.from(r, petName, ownerName, ownerEmail);
    }

    @GetMapping
    public List<ReminderDTO> all() {
        return repo.findAll()
                .stream()
                .map(this::map)
                .collect(Collectors.toList());
    }

    @GetMapping("/pet/{petId}")
    public List<ReminderDTO> getPetReminders(@PathVariable Long petId) {
        return repo.findByPetId(petId)
                .stream()
                .map(this::map)
                .collect(Collectors.toList());
    }

    @PostMapping
    public ReminderDTO create(@RequestBody Reminder r) {
        r.setId(null);
        if (r.getType() == null) {
            r.setType(r.getPetId() != null ? ReminderType.PET : ReminderType.GENERAL);
        }

        if (r.getType() == ReminderType.PET) {
            if (r.getPetId() == null) {
                throw new IllegalArgumentException("petId is required for Pet reminders");
            }
            Pet pet = pets.findById(r.getPetId())
                    .orElseThrow(() -> new IllegalArgumentException("Pet not found for reminder"));
            if (r.getOwnerId() == null) {
                r.setOwnerId(pet.getOwnerId());
            }
            if (r.getDate() == null) {
                throw new IllegalArgumentException("Reminder date is required");
            }
        } else {
            // General reminders should not be tied to a pet/owner unless explicitly provided
            r.setPetId(null);
        }
        r.setSent(false);
        r.setSentAt(null);
        Reminder saved = repo.save(r);
        return map(saved);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        repo.deleteById(id);
    }

    /** Manually trigger delivery of all due pet reminders (same logic as the hourly scheduler). */
    @PostMapping("/dispatch-due")
    public Map<String, Object> dispatchDue() {
        return reminderScheduler.sendDueReminders();
    }
}
