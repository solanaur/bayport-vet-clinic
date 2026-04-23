package com.bayport.web;

import com.bayport.entity.Pet;
import com.bayport.repository.*;
import com.bayport.service.AuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/recycle-bin")
public class RecycleBinController {

    private final PetRepository petRepository;
    private final AuditLogService auditLogService;
    private final com.bayport.repository.UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final ReminderRepository reminderRepository;
    private final BillingRecordRepository billingRecordRepository;
    private final ProcedureRepository procedureRepository;
    private final SaleRepository saleRepository;

    public RecycleBinController(PetRepository petRepository, 
                                AuditLogService auditLogService,
                                com.bayport.repository.UserRepository userRepository,
                                AppointmentRepository appointmentRepository,
                                PrescriptionRepository prescriptionRepository,
                                ReminderRepository reminderRepository,
                                BillingRecordRepository billingRecordRepository,
                                ProcedureRepository procedureRepository,
                                SaleRepository saleRepository) {
        this.petRepository = petRepository;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.reminderRepository = reminderRepository;
        this.billingRecordRepository = billingRecordRepository;
        this.procedureRepository = procedureRepository;
        this.saleRepository = saleRepository;
    }
    
    private boolean isAdmin() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        
        // Check Spring Security authorities first (most reliable)
        boolean hasAdminAuthority = auth.getAuthorities().stream()
                .anyMatch(a -> {
                    String authority = a.getAuthority();
                    return "ROLE_ADMIN".equalsIgnoreCase(authority) || 
                           "ADMIN".equalsIgnoreCase(authority) ||
                           "ADMINISTRATOR".equalsIgnoreCase(authority);
                });
        if (hasAdminAuthority) {
            return true;
        }
        
        // Fallback: Check user entity
        String username = auth.getName();
        java.util.Optional<com.bayport.entity.User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return false;
        }
        com.bayport.entity.User user = userOpt.get();
        
        // Check legacy role field
        if (user.getRole() != null) {
            String role = user.getRole().toLowerCase();
            if ("admin".equals(role) || "administrator".equals(role)) {
                return true;
            }
        }
        
        // Check roles set
        return user.getRoles().stream().anyMatch(r -> {
            String roleName = r.getName().toUpperCase();
            return "ROLE_ADMIN".equals(roleName) || 
                   "ADMIN".equals(roleName) ||
                   "ADMINISTRATOR".equals(roleName);
        });
    }

    @GetMapping
    public ResponseEntity<?> getRecycleBin() {
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access Denied", "message", "Only administrators can access recycle bin"));
        }
        Map<String, Object> result = new HashMap<>();
        
        // Get deleted pets
        List<Pet> deletedPets = petRepository.findByDeletedTrue();
        result.put("pets", deletedPets);
        
        // Note: For appointments, prescriptions, users - they are hard deleted
        // Only pets have soft delete implemented
        result.put("appointments", List.of());
        result.put("prescriptions", List.of());
        result.put("users", List.of());
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/pets")
    public ResponseEntity<?> getDeletedPets() {
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access Denied"));
        }
        return ResponseEntity.ok(petRepository.findByDeletedTrue());
    }

    @PostMapping("/pets/{id}/restore")
    public ResponseEntity<Map<String, Object>> restorePet(@PathVariable Long id) {
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "error", "Access Denied"));
        }
        try {
            Pet pet = petRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Pet not found"));
            
            pet.setDeleted(false);
            pet.setDeletedAt(null);
            pet.setDeletedBy(null);
            petRepository.save(pet);
            
            auditLogService.log("RESTORE", "Pet", String.valueOf(id), "Restored from recycle bin");
            
            return ResponseEntity.ok(Map.of("success", true, "message", "Pet restored successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/pets/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> permanentlyDeletePet(@PathVariable Long id) {
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "error", "Access Denied"));
        }
        try {
            // Verify pet exists
            if (!petRepository.existsById(id)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "error", "Pet not found"));
            }
            
            // Delete all related records first to avoid foreign key constraint violations
            
            // 1. Delete appointments
            appointmentRepository.deleteByPetId(id);
            
            // 2. Delete prescriptions
            prescriptionRepository.deleteByPetId(id);
            
            // 3. Delete reminders
            List<com.bayport.entity.Reminder> reminders = reminderRepository.findByPetId(id);
            if (!reminders.isEmpty()) {
                reminderRepository.deleteAll(reminders);
            }
            
            // 4. Delete billing records
            List<com.bayport.entity.BillingRecord> billingRecords = billingRecordRepository.findByPetId(id);
            if (!billingRecords.isEmpty()) {
                billingRecordRepository.deleteAll(billingRecords);
            }
            
            // 5. Delete procedures
            procedureRepository.deleteByPet_Id(id);
            
            // 6. Delete sales
            List<com.bayport.entity.Sale> sales = saleRepository.findAll().stream()
                    .filter(s -> s.getPetId() != null && s.getPetId().equals(id))
                    .toList();
            if (!sales.isEmpty()) {
                saleRepository.deleteAll(sales);
            }
            
            // 7. Finally, delete the pet
            petRepository.deleteById(id);
            
            auditLogService.log("HARD_DELETE", "Pet", String.valueOf(id), 
                    "Permanently deleted from recycle bin along with all related records");
            return ResponseEntity.ok(Map.of("success", true, "message", "Pet and all related records permanently deleted"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}

