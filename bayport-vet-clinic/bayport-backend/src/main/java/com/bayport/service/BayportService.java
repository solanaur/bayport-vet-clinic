package com.bayport.service;

import com.bayport.dto.AppointmentRequest;
import com.bayport.entity.*;
import com.bayport.exception.ResourceNotFoundException;
import com.bayport.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class BayportService {

    @Autowired
    private PetRepository petRepository;

    @Autowired
    private OwnerRepository ownerRepository;

    @Autowired
    private ProcedureRepository procedureRepository;

    @Autowired
    private ProcedureMedicationRepository procedureMedicationRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private OperationLogRepository operationLogRepository;

    @Autowired
    private BillingService billingService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private EmailService emailService;
    
    @Autowired(required = false)
    private com.bayport.service.NotificationService notificationService;
    
    @Autowired(required = false)
    private MfaCodeRepository mfaCodeRepository;

    // Pet operations
    public List<Pet> getAllPets() {
        // Use soft delete - only get non-deleted pets
        List<Pet> pets = petRepository.findByDeletedFalse();
        if (pets == null) {
            return new ArrayList<>();
        }
        // Initialize lazy-loaded procedures to avoid LazyInitializationException
        for (Pet pet : pets) {
            if (pet != null) {
                if (pet.getOwnerId() != null
                        && (pet.getOwner() == null || pet.getOwner().isBlank())) {
                    ownerRepository.findById(pet.getOwnerId())
                            .ifPresent(o -> pet.setOwner(o.getFullName()));
                }
                if (pet.getProcedures() != null) {
                    pet.getProcedures().size(); // Force initialization
                } else {
                    pet.setProcedures(new ArrayList<>());
                }
            }
        }
        return pets;
    }

    public Optional<Pet> getPetById(Long id) {
        // Use soft delete - only get non-deleted pets
        return petRepository.findByIdAndDeletedFalse(id);
    }

    /** Microchip lookup for scanners / REST API (cloud-ready). */
    public Optional<Pet> getPetByMicrochip(String microchip) {
        if (microchip == null || microchip.isBlank()) {
            return Optional.empty();
        }
        return petRepository.findFirstByMicrochipIgnoreCaseAndDeletedFalse(microchip.trim());
    }

    public Pet savePet(Pet pet) {
        validatePet(pet);
        syncOwnerName(pet);
        Pet savedPet = petRepository.save(pet);
        logOperation("PET_CREATED", "Added pet " + savedPet.getName(), savedPet.getId());
        return savedPet;
    }

    public Pet updatePet(Long id, Pet pet) {
        if (!petRepository.existsById(id)) {
            throw new ResourceNotFoundException("Pet not found with id: " + id);
        }
        pet.setId(id);
        validatePet(pet);
        syncOwnerName(pet);
        Pet updatedPet = petRepository.save(pet);
        logOperation("PET_UPDATED", "Updated pet " + updatedPet.getName(), updatedPet.getId());
        return updatedPet;
    }

    public void deletePet(Long id) {
        // Use soft delete instead of hard delete to avoid foreign key constraint violations
        softDeletePet(id);
    }
    
    public void softDeletePet(Long id) {
        Pet pet = petRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pet not found with id: " + id));
        
        if (Boolean.TRUE.equals(pet.getDeleted())) {
            return; // Already deleted
        }
        
        pet.setDeleted(true);
        pet.setDeletedAt(java.time.LocalDateTime.now());
        
        org.springframework.security.core.Authentication auth = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            pet.setDeletedBy(auth.getName());
        }
        
        petRepository.save(pet);
        logOperation("SOFT_DELETE", "Moved pet " + pet.getName() + " to recycle bin", id);
    }

    private void syncOwnerName(Pet pet) {
        if (pet.getOwnerId() != null) {
            Owner owner = ownerRepository.findById(pet.getOwnerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Owner not found with id: " + pet.getOwnerId()));
            pet.setOwner(owner.getFullName());
        }
    }

    private void validatePet(Pet pet) {
        if (pet == null) {
            throw new IllegalArgumentException("Pet payload is required");
        }
        if (!StringUtils.hasText(pet.getName())) {
            throw new IllegalArgumentException("Pet name is required");
        }
        if (pet.getOwnerId() == null) {
            throw new IllegalArgumentException("Owner is required");
        }
        ownerRepository.findById(pet.getOwnerId())
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found with id: " + pet.getOwnerId()));
    }

    public Pet addProcedureToPet(Long petId, Procedure procedure) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new ResourceNotFoundException("Pet not found with id: " + petId));
        if (pet.getProcedures() == null) {
            pet.setProcedures(new java.util.ArrayList<>());
        }

        if (procedure.getPerformedAt() == null) {
            procedure.setPerformedAt(LocalDate.now());
        }
        procedure.setPet(pet);
        Procedure savedProcedure = procedureRepository.save(procedure);
        pet.getProcedures().add(savedProcedure);
        Pet saved = petRepository.save(pet);

        if (savedProcedure.getCost() != null && savedProcedure.getCost().signum() > 0) {
            billingService.billProcedure(saved, savedProcedure);
        }

        return saved;
    }

    public Procedure updateProcedure(Long petId, Long procedureId, Procedure updated) {
        Procedure existing = procedureRepository.findById(procedureId)
                .orElseThrow(() -> new ResourceNotFoundException("Procedure not found with id: " + procedureId));
        if (existing.getPet() == null || !existing.getPet().getId().equals(petId)) {
            throw new IllegalStateException("Procedure does not belong to the specified pet");
        }
        existing.setPerformedAt(updated.getPerformedAt());
        existing.setName(updated.getName());
        existing.setNotes(updated.getNotes());
        existing.setMedications(updated.getMedications());
        existing.setCategory(updated.getCategory());
        existing.setLabType(updated.getLabType());
        existing.setCost(updated.getCost());
        existing.setVet(updated.getVet());
        return procedureRepository.save(existing);
    }

    public void deleteProcedure(Long petId, Long procedureId) {
        Procedure existing = procedureRepository.findById(procedureId)
                .orElseThrow(() -> new ResourceNotFoundException("Procedure not found with id: " + procedureId));
        if (existing.getPet() == null || !existing.getPet().getId().equals(petId)) {
            throw new IllegalStateException("Procedure does not belong to the specified pet");
        }
        procedureRepository.delete(existing);
    }

    // Appointment operations
    public List<Appointment> getAllAppointments() {
        return getAllAppointmentsForUser(null); // null = admin/receptionist sees all
    }

    /**
     * Get appointments for a specific date.
     */
    public List<Appointment> getAppointmentsByDate(LocalDate date) {
        List<Appointment> appointments = appointmentRepository.findByDate(date);
        // Initialize lazy-loaded pet relationships
        for (Appointment appointment : appointments) {
            if (appointment.getPet() != null) {
                appointment.getPet().getName();
            }
        }
        return appointments;
    }

    /**
     * Get appointments filtered by current user role.
     * Vets only see their own appointments; admin/receptionist see all.
     * 
     * @param currentUsername Current logged-in user's username (null for admin/receptionist)
     * @return Filtered list of appointments
     */
    public List<Appointment> getAllAppointmentsForUser(String currentUsername) {
        List<Appointment> appointments;
        
        // If current user is a vet, filter by vet name
        if (currentUsername != null) {
            User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
            if (currentUser != null && "vet".equalsIgnoreCase(currentUser.getRole())) {
                // Vet can only see their own appointments
                appointments = appointmentRepository.findByVet(currentUser.getName());
            } else {
                // Admin/receptionist see all appointments
                appointments = appointmentRepository.findAll();
            }
        } else {
            // No user context = show all (for admin/receptionist)
            appointments = appointmentRepository.findAll();
        }
        
        // Initialize lazy-loaded pet relationships
        for (Appointment appointment : appointments) {
            if (appointment.getPet() != null) {
                appointment.getPet().getName();
            }
        }
        return appointments;
    }

    public Optional<Appointment> getAppointmentById(Long id) {
        return appointmentRepository.findById(id);
    }

    /**
     * Creates a new appointment from an incoming request DTO. This method
     * looks up the referenced pet by {@code petId} instead of trusting any
     * embedded Pet JSON from the client.
     */
    public Appointment createAppointment(AppointmentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Appointment payload is required");
        }
        if (request.getPetId() == null) {
            throw new IllegalArgumentException("petId is required");
        }

        Pet pet = petRepository.findById(request.getPetId())
                .orElseThrow(() -> new ResourceNotFoundException("Pet not found with id: " + request.getPetId()));

        Appointment appointment = new Appointment();
        appointment.setPetId(pet.getId());
        // Prefer owner name from pet record to keep data consistent
        appointment.setOwner(pet.getOwner());

        if (request.getDate() == null || request.getDate().isBlank()) {
            throw new IllegalArgumentException("Appointment date is required");
        }
        appointment.setDate(LocalDate.parse(request.getDate()));
        appointment.setTime(request.getTime());
        appointment.setVet(request.getVet());

        // Let saveAppointment handle status defaulting, time validation,
        // overlap checks, code generation, and email notification.
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            appointment.setStatus(request.getStatus());
        }

        return saveAppointment(appointment);
    }

    public Appointment saveAppointment(Appointment appointment) {
        // Validate vet assignment
        if (appointment.getVet() == null || appointment.getVet().trim().isEmpty()) {
            throw new IllegalArgumentException("Veterinarian must be assigned to the appointment");
        }

        // Validate vet is active (check if vet exists in users with role 'vet')
        User vetUser = userRepository.findByUsername(appointment.getVet())
                .orElseGet(() -> userRepository.findAll().stream()
                        .filter(u -> "vet".equalsIgnoreCase(u.getRole()) && appointment.getVet().equalsIgnoreCase(u.getName()))
                        .findFirst()
                        .orElse(null));
        
        if (vetUser == null || !vetUser.isActive() || !"vet".equalsIgnoreCase(vetUser.getRole())) {
            throw new IllegalArgumentException("Selected veterinarian is not active or not found");
        }

        // Validate 30-minute time intervals
        validateTimeInterval(appointment.getTime());

        // Check for overlapping appointments for the same vet
        validateVetAvailability(appointment);

        // Enforce global 30-minute slot capacity (max 5 patients regardless of vet)
        enforceClinicSlotCapacity(appointment.getDate(), appointment.getTime(), appointment.getId());

        appointment.setStatus("Pending");
        
        // Generate unique appointment code if not provided
        if (appointment.getCode() == null || appointment.getCode().trim().isEmpty()) {
            appointment.setCode(generateUniqueAppointmentCode(appointment));
        }

        if (appointment.getOwner() == null && appointment.getPetId() != null) {
            petRepository.findById(appointment.getPetId())
                    .ifPresent(p -> appointment.setOwner(p.getOwner()));
        }

        Appointment savedAppointment = appointmentRepository.save(appointment);
        logOperation("APPT_CREATED", "Appointment created for " + savedAppointment.getOwner(), savedAppointment.getPetId());
        
        // Notify vet about new appointment
        if (notificationService != null && savedAppointment.getVet() != null) {
            try {
                String petName = savedAppointment.getPet() != null ? savedAppointment.getPet().getName() : "Unknown Pet";
                notificationService.notifyVetNewAppointment(savedAppointment.getVet(), savedAppointment.getOwner(), petName);
            } catch (Exception e) {
                // Log but don't fail the operation
            }
        }
        
        // DO NOT send email on appointment creation - only when vet approves
        // Email will be sent in approveAppointment method
        
        return savedAppointment;
    }

    /**
     * Validates that appointment time is in 30-minute intervals (ends in :00 or :30).
     */
    private void validateTimeInterval(String time) {
        if (time == null || time.trim().isEmpty()) {
            throw new IllegalArgumentException("Appointment time is required");
        }
        
        String[] parts = time.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid time format. Use HH:mm format");
        }
        
        try {
            int minutes = Integer.parseInt(parts[1]);
            if (minutes != 0 && minutes != 30) {
                throw new IllegalArgumentException("Appointment time must be in 30-minute intervals (e.g., 09:00, 09:30, 10:00)");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid time format. Minutes must be a number");
        }
    }

    /**
     * Generates a unique appointment code.
     */
    private String generateUniqueAppointmentCode(Appointment appointment) {
        LocalDate date = appointment.getDate() != null ? appointment.getDate() : LocalDate.now();
        String time = appointment.getTime() != null ? appointment.getTime().replace(":", "") : "0000";
        String baseCode = "APT-" + date.toString().replace("-", "") + "-" + time;

        // Ensure uniqueness by checking existing codes
        Long currentAppointmentId = appointment.getId();
        String code = baseCode;
        int suffix = 1;
        while (isAppointmentCodeInUse(code, currentAppointmentId)) {
            code = baseCode + "-" + suffix;
            suffix++;
        }

        return code;
    }

    private boolean isAppointmentCodeInUse(String code, Long currentAppointmentId) {
        return appointmentRepository.findAll().stream()
                .anyMatch(a -> code.equals(a.getCode()) &&
                        (currentAppointmentId == null || !currentAppointmentId.equals(a.getId())));
    }


    public Appointment updateAppointment(Long id, Appointment appointment) {
        if (appointment == null) {
            throw new IllegalArgumentException("Appointment payload is required");
        }
        appointment.setId(id);
        if (appointment.getCode() == null || appointment.getCode().trim().isEmpty()) {
            appointment.setCode(generateUniqueAppointmentCode(appointment));
        }
        // Validate time interval if time is being updated
        if (appointment.getTime() != null) {
            validateTimeInterval(appointment.getTime());
        }
        // Check for overlaps if date/time/vet is being updated
        if (appointment.getDate() != null && appointment.getTime() != null && appointment.getVet() != null) {
            validateVetAvailability(appointment);
            enforceClinicSlotCapacity(appointment.getDate(), appointment.getTime(), appointment.getId());
        }
        return appointmentRepository.save(appointment);
    }

    public void deleteAppointment(Long id) {
        logOperation("APPT_DELETED", "Removed appointment #" + id, null);
        appointmentRepository.deleteById(id);
    }

    public Appointment approveAppointment(Long id) {
        Optional<Appointment> appointmentOpt = appointmentRepository.findById(id);
        if (appointmentOpt.isPresent()) {
            Appointment appointment = appointmentOpt.get();
            appointment.setStatus("Approved by Vet");
            logOperation("APPT_APPROVED", "Appointment approved for " + appointment.getOwner(), appointment.getPetId());
            Appointment saved = appointmentRepository.save(appointment);
            
            // Load pet data for notifications and email
            final Pet pet = saved.getPetId() != null 
                ? petRepository.findById(saved.getPetId()).orElse(null) 
                : null;
            
            // Notify receptionist
            if (notificationService != null) {
                try {
                    String petName = (pet != null && pet.getName() != null && !pet.getName().trim().isEmpty()) 
                        ? pet.getName() 
                        : "Pet";
                    notificationService.notifyReceptionistAppointmentApproved(saved.getOwner(), petName);
                } catch (Exception e) {
                    // Log but don't fail the operation
                }
            }
            
            // Send email notification when vet approves - with accurate pet data
            if (emailService != null && pet != null && pet.getOwnerId() != null) {
                final Long ownerId = pet.getOwnerId();
                final Appointment finalSaved = saved;
                ownerRepository.findById(ownerId).ifPresent(owner -> {
                    if (owner.getEmail() != null && !owner.getEmail().trim().isEmpty()) {
                        try {
                            // Get accurate pet data - ensure no "Unknown" values
                            final String petName = (pet.getName() != null && !pet.getName().trim().isEmpty()) ? pet.getName() : "Pet";
                            final String petSpecies = (pet.getSpecies() != null && !pet.getSpecies().trim().isEmpty()) ? pet.getSpecies() : "";
                            final String petBreed = (pet.getBreed() != null && !pet.getBreed().trim().isEmpty()) ? pet.getBreed() : "";
                            final String petGender = (pet.getGender() != null && !pet.getGender().trim().isEmpty()) ? pet.getGender() : "";
                            final Integer petAgeValue = pet.getAge();
                            
                            // Create appointment with accurate pet data for email
                            Appointment emailAppt = new Appointment();
                            emailAppt.setDate(finalSaved.getDate());
                            emailAppt.setTime(finalSaved.getTime());
                            emailAppt.setVet(finalSaved.getVet() != null ? finalSaved.getVet() : "");
                            emailAppt.setCode(finalSaved.getCode() != null ? finalSaved.getCode() : "");
                            
                            // Set pet with accurate data
                            Pet emailPet = new Pet();
                            emailPet.setName(petName);
                            if (!petSpecies.isEmpty()) emailPet.setSpecies(petSpecies);
                            if (!petBreed.isEmpty()) emailPet.setBreed(petBreed);
                            if (petAgeValue != null) emailPet.setAge(petAgeValue);
                            if (!petGender.isEmpty()) emailPet.setGender(petGender);
                            emailAppt.setPet(emailPet);
                            
                            emailService.sendAppointmentReminder(owner.getEmail(), emailAppt);
                        } catch (Exception e) {
                            // Log but don't fail the operation
                            System.err.println("Failed to send appointment approval email: " + e.getMessage());
                        }
                    }
                });
            }
            
            return saved;
        }
        throw new ResourceNotFoundException("Appointment not found with id: " + id);
    }

    public Appointment markAppointmentDone(Long id) {
        Optional<Appointment> appointmentOpt = appointmentRepository.findById(id);
        if (appointmentOpt.isPresent()) {
            Appointment appointment = appointmentOpt.get();
            appointment.setStatus("Done");
            appointment.setCompletedAt(LocalDate.now());
            logOperation("APPT_DONE", "Appointment done for " + appointment.getOwner(), appointment.getPetId());
            return appointmentRepository.save(appointment);
        }
        throw new ResourceNotFoundException("Appointment not found with id: " + id);
    }

    // Prescription operations
    public List<Prescription> getAllPrescriptions() {
        List<Prescription> prescriptions = prescriptionRepository.findAll();
        return prescriptions != null ? prescriptions : new ArrayList<>();
    }

    public Optional<Prescription> getPrescriptionById(Long id) {
        return prescriptionRepository.findById(id);
    }

    public Prescription savePrescription(Prescription prescription) {
        // Clinical record only — pricing happens at POS / inventory
        prescription.setPrice(null);
        // Dosage / directions / notes removed from workflow — keep DB columns null
        prescription.setDosage(null);
        prescription.setDirections(null);
        prescription.setNotes(null);
        if (prescription.getPetId() != null) {
            petRepository.findById(prescription.getPetId()).ifPresent(p -> {
                prescription.setPet(p.getName());
                if (prescription.getOwner() == null) {
                    prescription.setOwner(p.getOwner());
                }
            });
        }
        Prescription savedPrescription = prescriptionRepository.save(prescription);
        logOperation("RX_CREATED", "Rx issued for " + savedPrescription.getPet() + " (" + savedPrescription.getDrug() + ")", savedPrescription.getPetId());
        
        // Notify pharmacist about new prescription
        if (notificationService != null) {
            try {
                notificationService.notifyPharmacistNewPrescription(savedPrescription.getPet(), savedPrescription.getOwner());
            } catch (Exception e) {
                // Log but don't fail the operation
            }
        }
        return savedPrescription;
    }

    public Prescription updatePrescription(Long id, Prescription prescription) {
        prescription.setId(id);
        prescription.setPrice(null);
        prescription.setDosage(null);
        prescription.setDirections(null);
        prescription.setNotes(null);
        return prescriptionRepository.save(prescription);
    }

    public void deletePrescription(Long id) {
        prescriptionRepository.deleteById(id);
    }

    public Prescription archivePrescription(Long id, boolean archived) {
        Prescription rx = prescriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found with id: " + id));
        rx.setArchived(archived);
        return prescriptionRepository.save(rx);
    }

    public Prescription dispensePrescription(Long id) {
        Optional<Prescription> prescriptionOpt = prescriptionRepository.findById(id);
        if (prescriptionOpt.isPresent()) {
            Prescription prescription = prescriptionOpt.get();
            prescription.setDispensed(true);
            prescription.setDispensedAt(LocalDate.now());
            // Auto-archive when dispensed
            prescription.setArchived(true);
            // Sales are recorded at Point of Sale (POS), not on dispense.
            logOperation("RX_DISPENSED", "Rx dispensed for " + prescription.getPet(), prescription.getPetId());
            return prescriptionRepository.save(prescription);
        }
        throw new ResourceNotFoundException("Prescription not found with id: " + id);
    }

    // User operations
    public List<User> getAllUsers() {
        List<User> users = userRepository.findAll();
        // Ensure we always return a list, never null
        return users != null ? users : new ArrayList<>();
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public User saveUser(User user) {
        validateUsername(user.getUsername(), null);
        
        // Check for email requirement (needed for MFA/OTP)
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email address is required for user accounts (needed for MFA/OTP authentication)");
        }
        
        // Check password from either 'password' or 'plainPassword' field (frontend compatibility)
        String passwordToEncode = user.getPassword();
        if (passwordToEncode == null || passwordToEncode.isBlank()) {
            passwordToEncode = user.getPlainPassword();
        }
        if (passwordToEncode == null || passwordToEncode.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        
        user.setPasswordHash(passwordEncoder.encode(passwordToEncode));
        user.setDisplayPassword(passwordToEncode); // Store plaintext for admin viewing
        user.setPassword(null);
        user.setPlainPassword(null); // Clear plain password after encoding
        
        // Ensure fullName is set from name if not provided
        if (user.getFullName() == null && user.getName() != null) {
            user.setFullName(user.getName());
        }
        if (user.getName() == null && user.getFullName() != null) {
            user.setName(user.getFullName());
        }
        
        // Set default values for new users
        if (user.getMfaEnabled() == null) {
            user.setMfaEnabled(false);
        }
        if (!user.isActive()) {
            user.setActive(true); // New users are active by default
        }

        normalizeLegacyRole(user);
        
        User savedUser = userRepository.save(user);
        syncSecurityRoles(savedUser.getId());
        savedUser = userRepository.findById(savedUser.getId()).orElse(savedUser);
        
        // Sync to Doctor table if role is VET
        if ("vet".equalsIgnoreCase(savedUser.getRole())) {
            syncUserToDoctor(savedUser);
        }
        
        return savedUser;
    }

    public User updateUser(Long id, User user) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        String oldRole = existing.getRole();
        validateUsername(user.getUsername(), id);
        existing.setName(user.getName());
        existing.setRole(normalizeLegacyRoleString(user.getRole()));
        existing.setUsername(user.getUsername());
        existing.setActive(user.isActive());
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            existing.setPasswordHash(passwordEncoder.encode(user.getPassword()));
            existing.setDisplayPassword(user.getPassword()); // Store plaintext for admin viewing
        }
        
        User updatedUser = userRepository.save(existing);
        syncSecurityRoles(updatedUser.getId());
        updatedUser = userRepository.findById(updatedUser.getId()).orElse(updatedUser);
        
        // Sync to Doctor table if role is VET
        if ("vet".equalsIgnoreCase(updatedUser.getRole())) {
            syncUserToDoctor(updatedUser);
        } else if ("vet".equalsIgnoreCase(oldRole) && !"vet".equalsIgnoreCase(updatedUser.getRole())) {
            // If user was a vet but is no longer, deactivate/remove doctor record
            deactivateDoctorForUser(updatedUser.getName());
        }
        
        return updatedUser;
    }

    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        // Delete MFA codes first to avoid foreign key constraint violation
        if (mfaCodeRepository != null) {
            mfaCodeRepository.deleteByUser(user);
        }
        
        // If user is a vet, deactivate the corresponding doctor record
        if ("vet".equalsIgnoreCase(user.getRole())) {
            deactivateDoctorForUser(user.getName());
        }
        
        userRepository.deleteById(id);
    }

    /**
     * Reset a user's password.
     * 
     * @param userId User ID
     * @param newPassword New password (will be hashed)
     */
    public void resetUserPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        if (newPassword == null || newPassword.trim().isEmpty() || newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setDisplayPassword(newPassword); // Store plaintext for admin viewing
        userRepository.save(user);
    }

    public void deleteOwnerCascade(Long ownerId) {
        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found with id: " + ownerId));
        petRepository.findByOwnerId(ownerId)
                .forEach(pet -> deletePetCascade(pet.getId(), false));
        List<Appointment> ownerAppointments = appointmentRepository.findByOwner(owner.getFullName());
        if (!ownerAppointments.isEmpty()) {
            appointmentRepository.deleteAll(ownerAppointments);
        }
        ownerRepository.delete(owner);
        logOperation("OWNER_DELETED", "Deleted owner " + owner.getFullName(), null);
    }

    public void resetSystemData(String username, String password) {
        User admin = authenticate(username, password)
                .filter(user -> "admin".equalsIgnoreCase(user.getRole()))
                .orElseThrow(() -> new SecurityException("Admin credentials are required"));

        procedureMedicationRepository.deleteAll();
        procedureRepository.deleteAll();
        prescriptionRepository.deleteAll();
        appointmentRepository.deleteAll();
        petRepository.deleteAll();
        ownerRepository.deleteAll();
        operationLogRepository.deleteAll();

        logOperation("SYSTEM_RESET", "Data reset triggered by " + admin.getUsername(), null);
    }

    // Operation log operations
    public List<OperationLog> getOperationLogsBetween(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(23, 59, 59);
        return operationLogRepository.findByDateRange(start, end);
    }

    private void logOperation(String type, String message, Long petId) {
        OperationLog log = new OperationLog();
        log.setTs(LocalDateTime.now());
        log.setType(type);
        log.setMessage(message);
        log.setPetId(petId);
        
        // Get current user from SecurityContext
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            String username = auth.getName();
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                // Use full name if available, otherwise username
                String displayName = user.getFullName() != null && !user.getFullName().isEmpty() 
                    ? user.getFullName() 
                    : (user.getName() != null && !user.getName().isEmpty() ? user.getName() : username);
                log.setUsername(displayName);
            } else {
                log.setUsername(username);
            }
        }
        
        operationLogRepository.save(log);
    }

    private void validateUsername(String username, Long currentId) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        userRepository.findByUsername(username)
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .ifPresent(u -> {
                    throw new IllegalArgumentException("Username already taken");
                });
    }

    public Optional<User> authenticate(String username, String password) {
        if (username == null || password == null) {
            return Optional.empty();
        }
        return userRepository.findByUsername(username)
                .filter(User::isActive)
                .filter(user -> {
                    // Check passwordHash first (legacy), then password field
                    if (user.getPasswordHash() != null) {
                        return passwordEncoder.matches(password, user.getPasswordHash());
                    } else if (user.getPassword() != null) {
                        return passwordEncoder.matches(password, user.getPassword());
                    }
                    return false;
                });
    }

    private void deletePetCascade(Long petId, boolean logAction) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new ResourceNotFoundException("Pet not found with id: " + petId));

        prescriptionRepository.deleteByPetId(petId);
        appointmentRepository.deleteByPetId(petId);
        procedureRepository.findByPet_Id(petId)
                .forEach(proc -> procedureMedicationRepository.deleteByProcedure_Id(proc.getId()));
        procedureRepository.deleteByPet_Id(petId);
        petRepository.deleteById(petId);

        if (logAction) {
            logOperation("PET_DELETED", "Deleted pet " + pet.getName(), petId);
        }
    }

    /**
     * Syncs a VET user to the DOCTORS table.
     * Creates a new doctor record if one doesn't exist, or updates existing one.
     */
    private void syncUserToDoctor(User user) {
        if (user == null || !"vet".equalsIgnoreCase(user.getRole())) {
            return;
        }

        Optional<Doctor> existingDoctor = doctorRepository.findByFullNameIgnoreCase(user.getName());
        
        Doctor doctor;
        if (existingDoctor.isPresent()) {
            doctor = existingDoctor.get();
        } else {
            doctor = new Doctor();
        }
        
        // Update doctor fields from user
        doctor.setFullName(user.getName());
        // Note: User entity doesn't have email/phone/specialty/licenseNo/hourlyRate fields
        // These would need to be added to User entity or passed separately if needed
        // For now, we'll set basic info and leave other fields as-is if updating
        
        doctorRepository.save(doctor);
        logOperation("DOCTOR_SYNCED", "Synced doctor record for user: " + user.getName(), null);
    }

    /**
     * Deactivates or removes the doctor record when a VET user is deleted or role changes.
     * For now, we'll delete the doctor record. In a production system, you might want to
     * add an 'active' field to Doctor and set it to false instead.
     */
    private void deactivateDoctorForUser(String userName) {
        if (userName == null || userName.isBlank()) {
            return;
        }
        
        Optional<Doctor> doctor = doctorRepository.findByFullNameIgnoreCase(userName);
        if (doctor.isPresent()) {
            doctorRepository.delete(doctor.get());
            logOperation("DOCTOR_DEACTIVATED", "Deactivated doctor record for: " + userName, null);
        }
    }

    /**
     * Ensures a veterinarian is free for the requested slot.
     */
    private void validateVetAvailability(Appointment appointment) {
        if (appointment.getDate() != null && appointment.getTime() != null) {
            List<Appointment> overlapping = appointmentRepository.findOverlappingAppointments(
                    appointment.getVet(), appointment.getDate(), appointment.getTime());
            if (!overlapping.isEmpty() && (appointment.getId() == null ||
                    overlapping.stream().noneMatch(a -> a.getId().equals(appointment.getId())))) {
                throw new IllegalArgumentException("Veterinarian already has an appointment at this time slot");
            }
        }
    }

    /**
     * Enforces the clinic-wide rule that only five patients can occupy the same
     * 30-minute block regardless of veterinarian assignment.
     */
    private void enforceClinicSlotCapacity(LocalDate date, String time, Long currentAppointmentId) {
        if (date == null || time == null) {
            return;
        }
        long activeCount = appointmentRepository.countActiveAppointments(date, time);
        long effectiveCount = activeCount;
        if (currentAppointmentId != null) {
            Optional<Appointment> existingOpt = appointmentRepository.findById(currentAppointmentId);
            if (existingOpt.isPresent()) {
                Appointment existing = existingOpt.get();
                if (date.equals(existing.getDate()) && time.equals(existing.getTime())) {
                    effectiveCount = Math.max(0, activeCount - 1);
                }
            }
        }
        if (effectiveCount >= 5) {
            throw new IllegalArgumentException("Selected time slot already has 5 patients. Please choose another time.");
        }
    }

    private void normalizeLegacyRole(User user) {
        if (user.getRole() != null) {
            user.setRole(normalizeLegacyRoleString(user.getRole()));
        }
    }

    /** Maps legacy receptionist/pharmacist to unified front_office role. */
    private String normalizeLegacyRoleString(String role) {
        if (role == null) return null;
        String r = role.trim().toLowerCase();
        if ("receptionist".equals(r) || "pharmacist".equals(r)) {
            return "front_office";
        }
        return r;
    }

    /** Keeps Spring Security authorities in sync with legacy {@code role} string (cloud-ready). */
    private void syncSecurityRoles(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getRole() == null || user.getRole().isBlank()) {
            return;
        }
        String key = user.getRole().toLowerCase();
        String roleName = switch (key) {
            case "admin" -> "ROLE_ADMIN";
            case "vet" -> "ROLE_VET";
            case "front_office" -> "ROLE_FRONT_OFFICE";
            default -> "ROLE_" + key.toUpperCase().replace("-", "_");
        };
        roleRepository.findByName(roleName).ifPresent(rn -> {
            user.getRoles().clear();
            user.getRoles().add(rn);
            userRepository.save(user);
        });
    }
}

