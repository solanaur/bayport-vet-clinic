package com.bayport.service;

import com.bayport.entity.Pet;
import com.bayport.entity.User;
import com.bayport.repository.PetRepository;
import com.bayport.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PetService {
    private final PetRepository petRepository;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    public PetService(PetRepository petRepository, AuditLogService auditLogService, UserRepository userRepository) {
        this.petRepository = petRepository;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
    }

    public List<Pet> getActivePets() {
        return petRepository.findByDeletedFalse();
    }

    public List<Pet> getDeletedPets() {
        return petRepository.findByDeletedTrue();
    }

    public Optional<Pet> getActivePetById(Long id) {
        return petRepository.findByIdAndDeletedFalse(id);
    }

    public void softDeletePet(Long id) {
        Pet pet = petRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pet not found"));

        if (Boolean.TRUE.equals(pet.getDeleted())) {
            return; // Already deleted
        }

        pet.setDeleted(true);
        pet.setDeletedAt(LocalDateTime.now());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            pet.setDeletedBy(auth.getName());
        }

        petRepository.save(pet);
        auditLogService.log("SOFT_DELETE", "Pet", String.valueOf(id), "Moved to recycle bin");
    }

    public void restorePet(Long id) {
        Pet pet = petRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pet not found"));

        pet.setDeleted(false);
        pet.setDeletedAt(null);
        pet.setDeletedBy(null);

        petRepository.save(pet);
        auditLogService.log("RESTORE", "Pet", String.valueOf(id), "Restored from recycle bin");
    }

    public void hardDeletePet(Long id) {
        if (!petRepository.existsById(id)) {
            throw new RuntimeException("Pet not found");
        }

        petRepository.deleteById(id);
        auditLogService.log("HARD_DELETE", "Pet", String.valueOf(id), "Permanently deleted");
    }
}
