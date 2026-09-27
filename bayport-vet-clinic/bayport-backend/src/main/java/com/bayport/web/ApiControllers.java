package com.bayport.web;

import com.bayport.dto.AppointmentRequest;
import com.bayport.dto.ReportSummary;
import com.bayport.entity.*;
import com.bayport.exception.ResourceNotFoundException;
import com.bayport.service.PdfService;
import com.bayport.service.BayportService;
import com.bayport.service.EmailService;
import com.bayport.service.InventoryService;
import com.bayport.service.PosService;
import com.bayport.service.ReportService;
import com.bayport.storage.FileStorageService;
import com.bayport.security.RecordAccessService;
import com.bayport.security.SecurityUtils;
import com.bayport.service.AuditLogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiControllers {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiControllers.class);

    private final BayportService bayportService;
    private final ReportService reportService;
    private final PdfService pdfService;
    private final String uploadDir;
    private final FileStorageService fileStorageService;
    private final com.bayport.auth.MfaService mfaService;
    private final com.bayport.repository.UserRepository userRepository;
    private final com.bayport.repository.OwnerRepository ownerRepository;
    private final EmailService emailService;
    private final PosService posService;
    private final InventoryService inventoryService;
    private final RecordAccessService recordAccessService;
    private final AuditLogService auditLogService;
    private final com.bayport.auth.TotpService totpService;
    private final boolean otpReturnWhenUndelivered;
    private static final Set<String> BYPASS_USERNAMES = Set.of("admin", "vet", "frontdesk", "recept", "pharm");

    public ApiControllers(
            BayportService bayportService,
            ReportService reportService,
            PdfService pdfService,
            @Value("${bayport.upload-dir:uploads}") String uploadDir,
            com.bayport.auth.MfaService mfaService,
            com.bayport.repository.UserRepository userRepository,
            com.bayport.repository.OwnerRepository ownerRepository,
            EmailService emailService,
            PosService posService,
            InventoryService inventoryService,
            FileStorageService fileStorageService,
            RecordAccessService recordAccessService,
            AuditLogService auditLogService,
            com.bayport.auth.TotpService totpService,
            @Value("${bayport.security.otp-return-when-undelivered:false}") boolean otpReturnWhenUndelivered
    ) {
        this.bayportService = bayportService;
        this.reportService = reportService;
        this.pdfService = pdfService;
        this.uploadDir = (uploadDir == null || uploadDir.isBlank()) ? "uploads" : uploadDir;
        this.mfaService = mfaService;
        this.userRepository = userRepository;
        this.ownerRepository = ownerRepository;
        this.emailService = emailService;
        this.posService = posService;
        this.inventoryService = inventoryService;
        this.fileStorageService = fileStorageService;
        this.recordAccessService = recordAccessService;
        this.auditLogService = auditLogService;
        this.totpService = totpService;
        this.otpReturnWhenUndelivered = otpReturnWhenUndelivered;
    }

    private ResponseEntity<Map<String, Object>> adminReportsDenied(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access Denied", "message", "Authentication required"));
        }
        if (!SecurityUtils.isAdministrator(auth, userRepository)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access Denied", "message", "Only administrators can access reports"));
        }
        return null;
    }

    private LocalDate[] resolveReportRange(String period, String from, String to) {
        LocalDate today = LocalDate.now();
        LocalDate start;
        LocalDate end;
        switch (period) {
            case "day" -> { start = today; end = today; }
            case "week" -> { start = today.minusDays(6); end = today; }
            case "month" -> { start = today.withDayOfMonth(1); end = today; }
            case "custom" -> {
                start = LocalDate.parse(Objects.requireNonNull(from, "from is required for custom period"));
                end = LocalDate.parse(Objects.requireNonNull(to, "to is required for custom period"));
            }
            default -> { start = today; end = today; }
        }
        return new LocalDate[] { start, end };
    }

    /* --------- Pets --------- */
    @GetMapping("/pets")
    public ResponseEntity<List<Pet>> listPets(){
        return ResponseEntity.ok(recordAccessService.filterPets(bayportService.getAllPets()));
    }

    /**
     * Recent POS sales (same contract as {@code SalesController}); exposed here so the route is
     * registered with the primary {@code /api} controller (avoids static-resource 404 in some deployments).
     */
    @GetMapping({"/sales/pos-recent", "/sales/pos/history"})
    @PreAuthorize("hasAnyRole('ADMIN','FRONT_OFFICE','RECEPTIONIST','PHARMACIST','VET','STAFF')")
    public ResponseEntity<List<Map<String, Object>>> salesPosRecent(
            @RequestParam(name = "limit", defaultValue = "40") int limit) {
        return ResponseEntity.ok(posService.recentPosSales(limit));
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<Pet> getPet(@PathVariable("id") long id) {
        try {
            recordAccessService.requirePetAccess(id);
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() == HttpStatus.FORBIDDEN) {
                auditLogService.log("UNAUTHORIZED_RECORD_ACCESS_ATTEMPT", "Pet", String.valueOf(id),
                        "Denied pet access", null);
            }
            throw ex;
        }
        return bayportService.getPetById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/pets/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPetProfilePdf(@PathVariable("id") long id) {
        recordAccessService.requirePetAccess(id);
        return bayportService.getPetById(id)
                .map(pet -> {
                    byte[] pdf = pdfService.buildPetProfilePdf(pet);
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=Pet_Profile_" + pet.getName().replaceAll("[^a-zA-Z0-9]", "_") + "_" + id + ".pdf")
                            .body(pdf);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/pets/pdf/all", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadAllPetsPdf() {
        List<Pet> pets = recordAccessService.filterPets(bayportService.getAllPetsWithDetails());
        if (pets.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        byte[] pdf = pdfService.buildAllPetsPdf(pets);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=All_Pets_Report_" + LocalDate.now() + ".pdf")
                .body(pdf);
    }

    @PostMapping("/pets")
    @PreAuthorize("hasAnyRole('ADMIN','FRONT_OFFICE','RECEPTIONIST','PHARMACIST','VET','STAFF')")
    public Pet createPet(@RequestBody Pet p){
        if (p.getProcedures() == null) p.setProcedures(new ArrayList<>());
        // Only admins/front office may set veterinarian assignment; vets cannot self-assign arbitrary IDs from client
        var me = recordAccessService.requireCurrentUser();
        if (recordAccessService.isVeterinarian(me) && !recordAccessService.isAdmin(me) && !recordAccessService.isFrontOffice(me)) {
            p.setAssignedVeterinarianId(me.getId());
        } else if (p.getAssignedVeterinarianId() != null) {
            userRepository.findById(p.getAssignedVeterinarianId()).ifPresent(u -> {
                if (!recordAccessService.isVeterinarian(u)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assigned user must be a veterinarian");
                }
            });
        }
        Pet saved = bayportService.savePet(p);
        auditLogService.log("PET_CREATED", "Pet", String.valueOf(saved.getId()), "Pet created", null);
        return saved;
    }

    @PutMapping("/pets/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FRONT_OFFICE','RECEPTIONIST','PHARMACIST','VET','STAFF')")
    public ResponseEntity<?> updatePet(@PathVariable("id") long id,
                                       @RequestBody Pet pet) {
        recordAccessService.requirePetAccess(id);
        if (bayportService.getPetById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var me = recordAccessService.requireCurrentUser();
        if (recordAccessService.isVeterinarian(me) && !recordAccessService.isAdmin(me) && !recordAccessService.isFrontOffice(me)) {
            // Vets cannot reassign pets away from themselves via mass assignment
            Pet existing = bayportService.getPetById(id).orElseThrow();
            pet.setAssignedVeterinarianId(existing.getAssignedVeterinarianId());
        }
        Pet updated = bayportService.updatePet(id, pet);
        auditLogService.log("PET_UPDATED", "Pet", String.valueOf(id), "Pet updated", null);
        return ResponseEntity.ok(updated);
    }


    @DeleteMapping("/pets/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FRONT_OFFICE','RECEPTIONIST','PHARMACIST')")
    public ResponseEntity<Void> deletePet(@PathVariable long id){
        if (bayportService.getPetById(id).isEmpty()) return ResponseEntity.notFound().build();
        bayportService.softDeletePet(id);
        auditLogService.log("PET_DELETED", "Pet", String.valueOf(id), "Pet soft-deleted", null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/pets/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String,String>> uploadPhoto(@PathVariable long id,
                      @RequestPart("file") MultipartFile file) throws IOException {
        recordAccessService.requirePetAccess(id);
        Pet pet = bayportService.getPetById(id).orElse(null);
        if (pet == null) return ResponseEntity.notFound().build();
        String photoUrl = fileStorageService.store(file);
        pet.setPhoto(photoUrl);
        bayportService.updatePet(id, pet);
        return ResponseEntity.ok(Map.of("url", pet.getPhoto()));
    }

    @PostMapping(value = "/inventory/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','FRONT_OFFICE','RECEPTIONIST','PHARMACIST')")
    public ResponseEntity<Map<String, String>> uploadInventoryPhoto(@PathVariable long id,
                                                                    @RequestPart("file") MultipartFile file) throws IOException {
        InventoryItem item = inventoryService.get(id);
        if (item.getCategory() != null && "SERVICE".equalsIgnoreCase(item.getCategory().trim())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Photos are not used for service items"));
        }
        String photoUrl = fileStorageService.store(file);
        item.setPhoto(photoUrl);
        inventoryService.update(id, item);
        return ResponseEntity.ok(Map.of("url", photoUrl));
    }

    @PostMapping("/pets/{id}/procedures")
    public ResponseEntity<Pet> addProcedure(@PathVariable long id, @RequestBody Procedure proc){
        try {
            recordAccessService.requirePetAccess(id);
            Pet pet = bayportService.addProcedureToPet(id, proc);
            return ResponseEntity.ok(pet);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/pets/{petId}/procedures/{procedureId}")
    public ResponseEntity<Procedure> updateProcedure(@PathVariable long petId,
                                                     @PathVariable long procedureId,
                                                     @RequestBody Procedure body) {
        try {
            recordAccessService.requirePetAccess(petId);
            Procedure updated = bayportService.updateProcedure(petId, procedureId, body);
            return ResponseEntity.ok(updated);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/pets/{petId}/procedures/{procedureId}")
    public ResponseEntity<Void> deleteProcedure(@PathVariable long petId,
                                                @PathVariable long procedureId) {
        try {
            recordAccessService.requirePetAccess(petId);
            bayportService.deleteProcedure(petId, procedureId);
            return ResponseEntity.noContent().build();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /* --------- Appointments --------- */
    @GetMapping("/appointments")
    public ResponseEntity<List<Appointment>> listAppointments(
            @RequestParam(name = "vet", required = false) String vet,
            @RequestParam(name = "unassigned", required = false) Boolean unassigned,
            @RequestParam(name = "currentUser", required = false) String currentUser,
            @RequestParam(name = "date", required = false) String date) {

        // Ignore client-supplied currentUser; scope from SecurityContext
        String authUser = recordAccessService.requireCurrentUser().getUsername();

        if (date != null && !date.isBlank()) {
            try {
                LocalDate appointmentDate = LocalDate.parse(date);
                List<Appointment> appointmentsByDate = recordAccessService.filterAppointments(
                        bayportService.getAppointmentsByDate(appointmentDate));
                return ResponseEntity.ok(appointmentsByDate);
            } catch (Exception e) {
                return ResponseEntity.badRequest().build();
            }
        }

        List<Appointment> all = recordAccessService.filterAppointments(
                bayportService.getAllAppointmentsForUser(authUser));

        if (vet != null && !vet.isBlank()) {
            all = all.stream().filter(a -> vet.equalsIgnoreCase(Objects.toString(a.getVet(), ""))).toList();
        }
        if (Boolean.TRUE.equals(unassigned)) {
            all = all.stream().filter(a -> a.getVet()==null || a.getVet().isBlank()).toList();
        }
        return ResponseEntity.ok(all);
    }

    @GetMapping("/appointments/vets")
    @PreAuthorize("hasAnyRole('ADMIN','FRONT_OFFICE','RECEPTIONIST','PHARMACIST','VET','VETERINARIAN','STAFF')")
    public List<Map<String, String>> listAppointmentVets() {
        return bayportService.listAssignableVeterinarians();
    }

    @GetMapping("/appointments/{id}")
    public ResponseEntity<Appointment> getAppointment(@PathVariable long id) {
        return bayportService.getAppointmentById(id)
                .map(a -> {
                    recordAccessService.requireAppointmentAccess(a);
                    return ResponseEntity.ok(a);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/appointments")
    public ResponseEntity<?> createAppt(@RequestBody AppointmentRequest request) {
        try {
            Appointment created = bayportService.createAppointment(request);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to create appointment: " + e.getMessage()));
        }
    }

    @PostMapping("/appointments/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable("id") long id) {
        try {
            bayportService.getAppointmentById(id).ifPresent(recordAccessService::requireAppointmentAccess);
            Appointment appointment = bayportService.approveAppointment(id);
            return ResponseEntity.ok(appointment);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/appointments/{id}/done")
    public ResponseEntity<Appointment> done(@PathVariable("id") long id) {
        try {
            bayportService.getAppointmentById(id).ifPresent(recordAccessService::requireAppointmentAccess);
            Appointment appointment = bayportService.markAppointmentDone(id);
            return ResponseEntity.ok(appointment);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/appointments/{id}/cancel")
    public ResponseEntity<?> cancelAppt(@PathVariable("id") long id) {
        try {
            bayportService.getAppointmentById(id).ifPresent(recordAccessService::requireAppointmentAccess);
            Appointment appointment = bayportService.cancelAppointment(id);
            return ResponseEntity.ok(appointment);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/appointments/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FRONT_OFFICE','RECEPTIONIST','PHARMACIST')")
    public ResponseEntity<Void> deleteAppt(@PathVariable("id") long id) {
        if (bayportService.getAppointmentById(id).isEmpty()) return ResponseEntity.notFound().build();
        bayportService.deleteAppointment(id);
        return ResponseEntity.noContent().build();
    }

    /* --------- Prescriptions --------- */
    @GetMapping("/prescriptions")
    public ResponseEntity<List<Prescription>> listRx(){
        return ResponseEntity.ok(recordAccessService.filterPrescriptions(bayportService.getAllPrescriptions()));
    }

    @GetMapping("/prescriptions/{id}")
    public ResponseEntity<Prescription> getRx(@PathVariable long id) {
        return bayportService.getPrescriptionById(id)
                .map(rx -> {
                    recordAccessService.requirePrescriptionAccess(rx);
                    return ResponseEntity.ok(rx);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/prescriptions")
    @PreAuthorize("hasAnyRole('ADMIN','VET','VETERINARIAN')")
    public Prescription createRx(@RequestBody Prescription r){
        if (r.getPetId() != null) {
            recordAccessService.requirePetAccess(r.getPetId());
        }
        Prescription saved = bayportService.savePrescription(r);
        auditLogService.log("PRESCRIPTION_CREATED", "Prescription", String.valueOf(saved.getId()), "Rx created", null);
        return saved;
    }

    @PutMapping("/prescriptions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','VET','VETERINARIAN','FRONT_OFFICE','RECEPTIONIST','PHARMACIST')")
    public ResponseEntity<Prescription> updateRx(@PathVariable long id, @RequestBody Prescription body) {
        Optional<Prescription> existing = bayportService.getPrescriptionById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        recordAccessService.requirePrescriptionAccess(existing.get());
        Prescription updated = bayportService.updatePrescription(id, body);
        auditLogService.log("PRESCRIPTION_UPDATED", "Prescription", String.valueOf(id), "Rx updated", null);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/prescriptions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','VET','VETERINARIAN')")
    public ResponseEntity<Void> deleteRx(@PathVariable long id) {
        Optional<Prescription> existing = bayportService.getPrescriptionById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        recordAccessService.requirePrescriptionAccess(existing.get());
        bayportService.deletePrescription(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/prescriptions/{id}/dispense")
    @PreAuthorize("hasAnyRole('ADMIN','FRONT_OFFICE','RECEPTIONIST','PHARMACIST')")
    public ResponseEntity<Prescription> dispense(@PathVariable long id){
        try {
            Prescription prescription = bayportService.dispensePrescription(id);
            return ResponseEntity.ok(prescription);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/prescriptions/{id}/archive")
    @PreAuthorize("hasAnyRole('ADMIN','VET','VETERINARIAN','FRONT_OFFICE','RECEPTIONIST','PHARMACIST')")
    public ResponseEntity<Prescription> archive(@PathVariable long id,
                                                @RequestParam(defaultValue = "true") boolean archived) {
        try {
            bayportService.getPrescriptionById(id).ifPresent(recordAccessService::requirePrescriptionAccess);
            return ResponseEntity.ok(bayportService.archivePrescription(id, archived));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /* --------- Users --------- */
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<User>> listUsers(){ 
        try {
            List<User> users = bayportService.getAllUsers();
            if (users == null) {
                users = new java.util.ArrayList<>();
            }
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(users);
        } catch (Exception e) {
            log.error("Error in listUsers", e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new java.util.ArrayList<>());
        }
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> getUser(@PathVariable long id) {
        return bayportService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public User createUser(@RequestBody User u){ return bayportService.saveUser(u); }
    
    // New OTP-based user creation endpoints
    @PostMapping("/users/send-otp")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> sendUserCreationOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
        }
        
        // Check if email is already in use
        java.util.Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is already registered"));
        }
        
        try {
            com.bayport.auth.MfaService.DeliveryResult sent = mfaService.sendOtpByEmail(email);
            Map<String, Object> body = new HashMap<>();
            body.put("status", "OTP_SENT");
            body.put("emailDelivered", sent.emailDelivered());
            body.put("from", emailService.getConfiguredFrom());
            // Desktop: always show the code so create-user can finish when Gmail accepts
            // the SMTP send but the message never reaches the inbox (spam / delayed).
            if (otpReturnWhenUndelivered && sent.code() != null) {
                body.put("localOtp", sent.code());
            }
            if (sent.emailDelivered()) {
                String from = emailService.getConfiguredFrom();
                String hint = "OTP emailed from " + (from == null || from.isBlank() ? "the clinic mailbox" : from)
                        + ". Check Inbox and Spam for " + email.trim() + ".";
                if (otpReturnWhenUndelivered && sent.code() != null) {
                    hint += " If it did not arrive, use this code: " + sent.code();
                }
                body.put("message", hint);
            } else if (otpReturnWhenUndelivered && sent.code() != null) {
                body.put("message", "Email could not be sent. Enter this one-time code: " + sent.code());
            } else {
                body.put("message", "OTP generated. Ask an administrator for the code if email did not arrive.");
            }
            return ResponseEntity.ok(body);
        } catch (ResponseStatusException rse) {
            return ResponseEntity.status(rse.getStatusCode())
                    .body(Map.of("error", rse.getReason() != null ? rse.getReason() : "Request blocked"));
        } catch (Exception e) {
            String detail = e.getMessage() == null || e.getMessage().isBlank() ? "Failed to send OTP" : e.getMessage();
            return ResponseEntity.status(500).body(Map.of("error", detail));
        }
    }
    
    @PostMapping("/users/verify-otp-create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> verifyOtpAndCreateUser(@RequestBody Map<String, Object> request) {
        String email = (String) request.get("email");
        String otp = (String) request.get("otp");
        String name = (String) request.get("name");
        String username = (String) request.get("username");
        String password = (String) request.get("password");
        String role = (String) request.get("role");
        
        if (email == null || otp == null || name == null || username == null || role == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "All fields are required"));
        }
        
        if (password == null || password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters"));
        }

        String normalizedRole = role.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("admin", "vet", "veterinarian", "front_office", "receptionist", "pharmacist", "staff").contains(normalizedRole)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role"));
        }
        
        if (!mfaService.verifyOtpByEmail(email, otp)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired OTP"));
        }
        
        try {
            User newUser = new User();
            newUser.setName(name);
            newUser.setFullName(name);
            newUser.setUsername(username);
            newUser.setEmail(email);
            newUser.setRole(role);
            newUser.setPassword(password);
            newUser.setMfaEnabled(true);
            
            User created = bayportService.saveUser(newUser);
            Map<String, Object> body = new HashMap<>();
            body.put("id", created.getId());
            body.put("name", created.getFullName() != null ? created.getFullName() : created.getName());
            body.put("username", created.getUsername());
            body.put("email", created.getEmail());
            body.put("role", created.getRole());
            body.put("mfaEnabled", true);

            String createdUsername = created.getUsername() == null ? "" : created.getUsername().toLowerCase(Locale.ROOT);
            if (!BYPASS_USERNAMES.contains(createdUsername)) {
                com.bayport.auth.TotpService.Enrollment enrollment = totpService.enroll(created.getUsername());
                created.setTotpSecret(enrollment.secret());
                created.setMfaEnabled(true);
                created = userRepository.save(created);
                body.put("totpSecret", enrollment.secret());
                body.put("totpUri", enrollment.otpauthUri());
                body.put("totpIssuer", com.bayport.auth.TotpService.ISSUER);
                body.put("mfaType", "TOTP");
            }

            auditLogService.log("USER_CREATED", "User", String.valueOf(created.getId()), "User created", null);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to create user"));
        }
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> updateUser(@PathVariable long id, @RequestBody User u){
        Optional<User> existingOpt = bayportService.getUserById(id);
        if (existingOpt.isEmpty()) return ResponseEntity.notFound().build();
        String oldRole = existingOpt.get().getRole();
        User updated = bayportService.updateUser(id, u);
        if (oldRole != null && updated.getRole() != null && !oldRole.equalsIgnoreCase(updated.getRole())) {
            auditLogService.log("ROLE_CHANGED", "User", String.valueOf(id),
                    "Role changed", null);
        }
        auditLogService.log("USER_UPDATED", "User", String.valueOf(id), "User updated", null);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/users/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getUserPassword(@PathVariable long id) {
        // Passwords are never retrievable — only hashes are stored
        if (bayportService.getUserById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(HttpStatus.GONE)
                .body(Map.of(
                        "error", "Password viewing is disabled for security",
                        "hasPassword", true,
                        "password", ""
                ));
    }

    @PostMapping("/users/{id}/send-edit-otp")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> sendEditCredentialsOtp(@PathVariable long id) {
        return bayportService.getUserById(id)
                .map(user -> {
                    if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                                .body(Map.of("error", "User email is required for OTP"));
                    }
                    try {
                        mfaService.sendMfaCode(user);
                        return ResponseEntity.ok(Map.of("status", "OTP_SENT", "message", "OTP sent to user email"));
                    } catch (ResponseStatusException rse) {
                        return ResponseEntity.status(rse.getStatusCode())
                                .body(Map.of("error", rse.getReason() != null ? rse.getReason() : "Request blocked"));
                    } catch (Exception e) {
                        return ResponseEntity.status(500)
                                .body(Map.of("error", "Failed to send OTP"));
        }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/users/{id}/edit-credentials")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> editCredentials(@PathVariable long id,
                                              @RequestBody Map<String, Object> request) {
        String otp = (String) request.get("otp");
        String username = (String) request.get("username");
        String email = (String) request.get("email");
        String password = (String) request.get("password");
        
        return bayportService.getUserById(id)
                .map(user -> {
                    if (otp == null || otp.trim().isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "OTP is required"));
                    }
                    
                    if (!mfaService.verifyCode(user, otp)) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "Invalid or expired OTP"));
                    }
                    
                    try {
                        boolean updated = false;
                        if (username != null && !username.trim().isEmpty() && !username.equals(user.getUsername())) {
                            user.setUsername(username);
                            updated = true;
                        }
                        if (email != null && !email.trim().isEmpty() && !email.equals(user.getEmail())) {
                            java.util.Optional<User> existing = userRepository.findByEmail(email);
                            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                                return ResponseEntity.badRequest()
                                        .body(Map.of("error", "Email is already registered to another user"));
                            }
                            user.setEmail(email);
                            updated = true;
                        }
                        if (password != null && !password.trim().isEmpty()) {
                            if (password.length() < 6) {
                                return ResponseEntity.badRequest()
                                        .body(Map.of("error", "Password must be at least 6 characters"));
                            }
                            user.setPassword(password);
                            updated = true;
                        }
                        
                        if (updated) {
                            bayportService.updateUser(id, user);
                            auditLogService.log("PASSWORD_CHANGED", "User", String.valueOf(id),
                                    "Credentials updated via admin OTP flow", null);
                            return ResponseEntity.ok(Map.of("success", true, "message", "Credentials updated successfully"));
                        } else {
            return ResponseEntity.badRequest()
                                    .body(Map.of("error", "No changes provided"));
                        }
        } catch (Exception e) {
            return ResponseEntity.status(500)
                                .body(Map.of("error", "Failed to update credentials"));
        }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable long id){
        if (bayportService.getUserById(id).isEmpty()) return ResponseEntity.notFound().build();
        bayportService.deleteUser(id);
        auditLogService.log("USER_DISABLED", "User", String.valueOf(id), "User deleted", null);
        return ResponseEntity.noContent().build();
    }

    /* --------- Reports & Ops --------- */
    @GetMapping("/ops/log")
    @PreAuthorize("hasRole('ADMIN')")
    public List<OperationLog> opsLog(
            @RequestParam String from,
            @RequestParam String to
    ){
        LocalDate f = LocalDate.parse(from);
        LocalDate t = LocalDate.parse(to);
        return bayportService.getOperationLogsBetween(f, t);
    }

    /**
     * Recent POS sales for the front-desk screen. Uses the same URL prefix as {@code /reports/summary}
     * so it resolves reliably (avoids static-resource 404 on {@code /sales/pos-recent} in some deployments).
     * Intentionally not restricted to admin — same audience as checkout.
     */
    @GetMapping("/reports/pos-sales-recent")
    public ResponseEntity<List<Map<String, Object>>> reportsPosSalesRecent(
            @RequestParam(name = "limit", defaultValue = "40") int limit) {
        return ResponseEntity.ok(posService.recentPosSales(limit));
    }

    @GetMapping("/reports/summary")
    public ResponseEntity<?> summary(
            @RequestParam(name = "period", defaultValue = "day") String period,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to
    ){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        ResponseEntity<Map<String, Object>> denied = adminReportsDenied(auth);
        if (denied != null) {
            return denied;
        }

        try {
            LocalDate[] range = resolveReportRange(period, from, to);
            return ResponseEntity.ok(reportService.summarize(range[0], range[1], period));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Unable to generate summary", "details", e.getMessage()));
        }
    }

    /* --------- Health / Status --------- */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "UP");
        body.put("timestamp", java.time.Instant.now().toString());
        body.put("mailConfigured", emailService.isConfigured());
        body.put("canSendToAnyRecipient", emailService.canSendToAnyRecipient());
        body.put("emailProvider", emailService.describeProvider());
        body.put("resendSandbox", emailService.isResendSandbox());
        if (emailService.usesBrevo()) {
            body.put("brevoFrom", emailService.getBrevoFromEmail());
        }
        body.put("netlifyEmailRelay", emailService.usesNetlifyRelay());
        if (emailService.isResendSandbox()) {
            body.put("resendFrom", emailService.getResendFrom());
            body.put("resendSandboxNote", emailService.resendSandboxNote());
        }
        body.put("renderSmtpBlocked", true);
        body.put("mailHost", emailService.usesResend() ? "api.resend.com" : "smtp.gmail.com");
        body.put("mailPort", emailService.usesResend() ? "443" : System.getenv().getOrDefault("SPRING_MAIL_PORT", "465"));
        body.put("schedulingEnabled", true);
        try {
            long petCount = bayportService.countActivePets();
            body.put("databaseConnected", true);
            body.put("petCount", petCount);
        } catch (Exception dbEx) {
            body.put("databaseConnected", false);
            body.put("databaseError", dbEx.getMessage());
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/pets/search")
    public ResponseEntity<List<Pet>> searchPets(@RequestParam(name = "name") String name) {
        return ResponseEntity.ok(bayportService.findPetsByName(name));
    }

    @GetMapping(value = "/prescriptions/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPrescriptionPdf(@PathVariable long id,
                                                          @RequestParam(name = "group", required = false) String groupKey) {
        return bayportService.getPrescriptionById(id)
                .map(firstPrescription -> {
                    List<Prescription> prescriptions = enrichPrescriptionsForPdf(resolvePrescriptionGroup(firstPrescription, groupKey));
                    byte[] pdf = pdfService.buildPrescriptionPdf(prescriptions);
                    bayportService.markPrescriptionGroupPrinted(id, groupKey);
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=Prescription_" + firstPrescription.getPet() + "_" + firstPrescription.getDate() + ".pdf")
                            .body(pdf);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/prescriptions/{id}/email")
    public ResponseEntity<Map<String, Object>> emailPrescriptionToOwner(@PathVariable long id,
                                                                        @RequestParam(name = "group", required = false) String groupKey,
                                                                        @RequestBody(required = false) Map<String, String> request) {
        return bayportService.getPrescriptionById(id)
                .map(firstPrescription -> {
                    List<Prescription> prescriptions = resolvePrescriptionGroup(firstPrescription, groupKey);
                    String to = request != null ? request.get("to") : null;
                    if (to == null || to.isBlank()) {
                        if (firstPrescription.getPetId() != null) {
                            to = bayportService.getPetById(firstPrescription.getPetId())
                                    .flatMap(p -> p.getOwnerId() == null ? Optional.empty() : ownerRepository.findById(p.getOwnerId()))
                                    .map(Owner::getEmail)
                                    .orElse(null);
                        }
                    }
                    if (to == null || to.isBlank()) {
                        throw new IllegalArgumentException("Owner email is not available for this prescription.");
                    }
                    String subject = (request != null && request.get("subject") != null && !request.get("subject").isBlank())
                            ? request.get("subject")
                            : "Prescription for " + Optional.ofNullable(firstPrescription.getPet()).orElse("your pet");
                    String body = (request != null && request.get("message") != null && !request.get("message").isBlank())
                            ? request.get("message")
                            : buildPrescriptionEmailBody(prescriptions);
                    try {
                        emailService.sendEmail(to, subject, body);
                    } catch (RuntimeException mailEx) {
                        Map<String, Object> err = new HashMap<>();
                        err.put("status", "failed");
                        err.put("error", emailService.toClientMessage(mailEx));
                        err.put("resendSandbox", emailService.isResendSandbox());
                        if (emailService.isResendSandbox()) {
                            err.put("resendSandboxNote", emailService.resendSandboxNote());
                        }
                        return ResponseEntity.status(502).body(err);
                    }
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "sent");
                    response.put("to", to);
                    response.put("count", prescriptions.size());
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private List<Prescription> enrichPrescriptionsForPdf(List<Prescription> prescriptions) {
        for (Prescription rx : prescriptions) {
            if (rx.getPetId() != null) {
                bayportService.getPetById(rx.getPetId()).ifPresent(pet -> {
                    rx.setPetEntity(pet);
                    if (rx.getOwner() == null || rx.getOwner().isBlank()) {
                        rx.setOwner(pet.getOwner());
                    }
                    if (rx.getPet() == null || rx.getPet().isBlank()) {
                        rx.setPet(pet.getName());
                    }
                });
            }
            if ((rx.getPrescriberLicenseNo() == null || rx.getPrescriberLicenseNo().isBlank())
                    && rx.getPrescriber() != null && !rx.getPrescriber().isBlank()) {
                String license = bayportService.lookupPrescriberLicenseNo(rx.getPrescriber());
                if (license != null && !license.isBlank()) {
                    rx.setPrescriberLicenseNo(license);
                }
            }
        }
        return prescriptions;
    }

    private List<Prescription> resolvePrescriptionGroup(Prescription firstPrescription, String groupKey) {
        if (groupKey != null && !groupKey.isBlank()) {
            String[] parts = groupKey.split("_", 3);
            if (parts.length >= 3) {
                Long petId = Long.parseLong(parts[0]);
                String date = parts[1];
                String prescriber = parts[2];
                List<Prescription> grouped = bayportService.getAllPrescriptions().stream()
                        .filter(p -> p.getPetId() != null && p.getPetId().equals(petId))
                        .filter(p -> p.getDate() != null && p.getDate().toString().equals(date))
                        .filter(p -> prescriber.equals(p.getPrescriber()))
                        .collect(java.util.stream.Collectors.toList());
                if (!grouped.isEmpty()) {
                    return grouped;
                }
            }
        }
        List<Prescription> grouped = bayportService.getAllPrescriptions().stream()
                .filter(p -> p.getPetId() != null && p.getPetId().equals(firstPrescription.getPetId()))
                .filter(p -> p.getDate() != null && firstPrescription.getDate() != null &&
                        p.getDate().equals(firstPrescription.getDate()))
                .filter(p -> firstPrescription.getPrescriber() != null &&
                        firstPrescription.getPrescriber().equals(p.getPrescriber()))
                .collect(java.util.stream.Collectors.toList());
        return grouped.isEmpty() ? java.util.Collections.singletonList(firstPrescription) : grouped;
    }

    private String buildPrescriptionEmailBody(List<Prescription> prescriptions) {
        Prescription first = prescriptions.get(0);
        StringBuilder body = new StringBuilder();
        body.append("Hello ").append(Optional.ofNullable(first.getOwner()).orElse("Pet Owner")).append(",\n\n");
        body.append("Please see your pet prescription details below:\n\n");
        body.append("Pet: ").append(Optional.ofNullable(first.getPet()).orElse("N/A")).append("\n");
        body.append("Date: ").append(first.getDate() != null ? first.getDate().toString() : "N/A").append("\n");
        body.append("Prescriber: ").append(Optional.ofNullable(first.getPrescriber()).orElse("N/A")).append("\n\n");
        body.append("Medicines:\n");
        for (Prescription p : prescriptions) {
            body.append("- ").append(Optional.ofNullable(p.getDrug()).orElse("N/A"));
            if (p.getDosage() != null && !p.getDosage().isBlank()) {
                body.append(" | Dosage: ").append(p.getDosage());
            }
            if (p.getDirections() != null && !p.getDirections().isBlank()) {
                body.append(" | Instructions: ").append(p.getDirections());
            }
            body.append("\n");
        }
        body.append("\nThank you,\nBayport Veterinary Clinic");
        return body.toString();
    }

    @GetMapping(value = "/reports/summary/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadReportPdf(
            @RequestParam(name = "period", defaultValue = "day") String period,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "preparedBy", required = false) String preparedBy
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!SecurityUtils.isAdministrator(auth, userRepository)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(("{\"error\":\"Access Denied\",\"message\":\"Only administrators can export reports\"}")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        try {
            LocalDate[] range = resolveReportRange(period, from, to);
            ReportSummary summary = reportService.summarize(range[0], range[1], period);
            byte[] pdf = pdfService.buildSummaryPdf(summary, preparedBy);

            String filename = String.format("Bayport_Summary_%s_%s.pdf",
                    range[0].toString(), range[1].toString());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .body(pdf);
        } catch (Exception e) {
            log.error("reports/summary/pdf failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/admin/reset-data")
    public ResponseEntity<Map<String, Object>> resetData(@RequestBody AdminResetRequest request) {
        bayportService.resetSystemData(request.username(), request.password());
        return ResponseEntity.ok(Map.of("success", true));
    }

    public record AdminResetRequest(String username, String password) {}
}
