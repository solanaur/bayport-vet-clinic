package com.bayport.web;

import com.bayport.dto.AppointmentRequest;
import com.bayport.dto.PosSaleHistoryDto;
import com.bayport.dto.ReportSummary;
import com.bayport.entity.*;
import com.bayport.service.PdfService;
import com.bayport.service.BayportService;
import com.bayport.service.PosService;
import com.bayport.service.ReportService;
import com.bayport.storage.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiControllers {

    private final BayportService bayportService;
    private final ReportService reportService;
    private final PdfService pdfService;
    private final String uploadDir;
    private final FileStorageService fileStorageService;
    private final com.bayport.auth.MfaService mfaService;
    private final com.bayport.repository.UserRepository userRepository;
    private final PosService posService;

    public ApiControllers(
            BayportService bayportService,
            ReportService reportService,
            PdfService pdfService,
            @Value("${bayport.upload-dir:uploads}") String uploadDir,
            com.bayport.auth.MfaService mfaService,
            com.bayport.repository.UserRepository userRepository,
            PosService posService,
            FileStorageService fileStorageService
    ) {
        this.bayportService = bayportService;
        this.reportService = reportService;
        this.pdfService = pdfService;
        this.uploadDir = (uploadDir == null || uploadDir.isBlank()) ? "uploads" : uploadDir;
        this.mfaService = mfaService;
        this.userRepository = userRepository;
        this.posService = posService;
        this.fileStorageService = fileStorageService;
    }

    /* --------- Pets --------- */
    @GetMapping("/pets")
    public ResponseEntity<List<Pet>> listPets(){
        return ResponseEntity.ok(bayportService.getAllPets());
    }

    /**
     * Recent POS sales (same contract as {@code SalesController}); exposed here so the route is
     * registered with the primary {@code /api} controller (avoids static-resource 404 in some deployments).
     */
    @GetMapping({"/sales/pos-recent", "/sales/pos/history"})
    public ResponseEntity<List<PosSaleHistoryDto>> salesPosRecent(
            @RequestParam(name = "limit", defaultValue = "40") int limit) {
        return ResponseEntity.ok(posService.recentPosSales(limit));
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<Pet> getPet(@PathVariable("id") long id) {
        return bayportService.getPetById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/pets/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPetProfilePdf(@PathVariable("id") long id) {
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
        List<Pet> pets = bayportService.getAllPets();
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
    public Pet createPet(@RequestBody Pet p){
        if (p.getProcedures() == null) p.setProcedures(new ArrayList<>());
        return bayportService.savePet(p);
    }

    @PutMapping("/pets/{id}")
    public ResponseEntity<?> updatePet(@PathVariable("id") long id,
                                       @RequestBody Pet pet) {

        // If no pet with this id exists, return 404
        if (bayportService.getPetById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Make sure procedures list is never null
        if (pet.getProcedures() == null) {
            pet.setProcedures(new ArrayList<>());
        }

        // Delegate to the service and return the updated pet
        return ResponseEntity.ok(bayportService.updatePet(id, pet));
    }


    @DeleteMapping("/pets/{id}")
    public ResponseEntity<Void> deletePet(@PathVariable long id){
        if (bayportService.getPetById(id).isEmpty()) return ResponseEntity.notFound().build();
        // Use soft delete - move to recycle bin
        bayportService.softDeletePet(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value="/pets/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String,String>> uploadPhoto(@PathVariable long id,
                      @RequestPart("file") MultipartFile file) throws IOException {
        Pet pet = bayportService.getPetById(id).orElse(null);
        if (pet == null) return ResponseEntity.notFound().build();
        String photoUrl = fileStorageService.store(file);
        pet.setPhoto(photoUrl);
        bayportService.updatePet(id, pet);
        return ResponseEntity.ok(Map.of("url", pet.getPhoto()));
    }

    @PostMapping("/pets/{id}/procedures")
    public ResponseEntity<Pet> addProcedure(@PathVariable long id, @RequestBody Procedure proc){
        try {
            Pet pet = bayportService.addProcedureToPet(id, proc);
            return ResponseEntity.ok(pet);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/pets/{petId}/procedures/{procedureId}")
    public ResponseEntity<Procedure> updateProcedure(@PathVariable long petId,
                                                     @PathVariable long procedureId,
                                                     @RequestBody Procedure body) {
        try {
            Procedure updated = bayportService.updateProcedure(petId, procedureId, body);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/pets/{petId}/procedures/{procedureId}")
    public ResponseEntity<Void> deleteProcedure(@PathVariable long petId,
                                                @PathVariable long procedureId) {
        try {
            bayportService.deleteProcedure(petId, procedureId);
            return ResponseEntity.noContent().build();
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

        // If date is provided, return appointments for that specific date
        if (date != null && !date.isBlank()) {
            try {
                LocalDate appointmentDate = LocalDate.parse(date);
                List<Appointment> appointmentsByDate = bayportService.getAppointmentsByDate(appointmentDate);
                return ResponseEntity.ok(appointmentsByDate);
            } catch (Exception e) {
                return ResponseEntity.badRequest().build();
            }
        }

        List<Appointment> all = bayportService.getAllAppointmentsForUser(currentUser);

        if (vet != null && !vet.isBlank()) {
            all = all.stream().filter(a -> vet.equalsIgnoreCase(Objects.toString(a.getVet(), ""))).toList();
        }
        if (Boolean.TRUE.equals(unassigned)) {
            all = all.stream().filter(a -> a.getVet()==null || a.getVet().isBlank()).toList();
        }
        return ResponseEntity.ok(all);
    }

    @GetMapping("/appointments/{id}")
    public ResponseEntity<Appointment> getAppointment(@PathVariable long id) {
        return bayportService.getAppointmentById(id)
                .map(ResponseEntity::ok)
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
    public ResponseEntity<Appointment> approve(@PathVariable("id") long id) {
        try {
            Appointment appointment = bayportService.approveAppointment(id);
            return ResponseEntity.ok(appointment);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/appointments/{id}/done")
    public ResponseEntity<Appointment> done(@PathVariable("id") long id) {
        try {
            Appointment appointment = bayportService.markAppointmentDone(id);
            return ResponseEntity.ok(appointment);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/appointments/{id}")
    public ResponseEntity<Void> deleteAppt(@PathVariable("id") long id) {
        if (bayportService.getAppointmentById(id).isEmpty()) return ResponseEntity.notFound().build();
        bayportService.deleteAppointment(id);
        return ResponseEntity.noContent().build();
    }

    /* --------- Prescriptions --------- */
    @GetMapping("/prescriptions")
    public ResponseEntity<List<Prescription>> listRx(){
        return ResponseEntity.ok(bayportService.getAllPrescriptions());
    }

    @GetMapping("/prescriptions/{id}")
    public ResponseEntity<Prescription> getRx(@PathVariable long id) {
        return bayportService.getPrescriptionById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/prescriptions")
    public Prescription createRx(@RequestBody Prescription r){ return bayportService.savePrescription(r); }

    @PutMapping("/prescriptions/{id}")
    public ResponseEntity<Prescription> updateRx(@PathVariable long id, @RequestBody Prescription body) {
        if (bayportService.getPrescriptionById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bayportService.updatePrescription(id, body));
    }

    @DeleteMapping("/prescriptions/{id}")
    public ResponseEntity<Void> deleteRx(@PathVariable long id) {
        if (bayportService.getPrescriptionById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        bayportService.deletePrescription(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/prescriptions/{id}/dispense")
    public ResponseEntity<Prescription> dispense(@PathVariable long id){
        try {
            Prescription prescription = bayportService.dispensePrescription(id);
            return ResponseEntity.ok(prescription);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/prescriptions/{id}/archive")
    public ResponseEntity<Prescription> archive(@PathVariable long id,
                                                @RequestParam(defaultValue = "true") boolean archived) {
        try {
            return ResponseEntity.ok(bayportService.archivePrescription(id, archived));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /* --------- Users --------- */
    @GetMapping("/users")
    public ResponseEntity<List<User>> listUsers(){ 
        try {
            List<User> users = bayportService.getAllUsers();
            // Log for debugging
            System.out.println("GET /api/users - Found " + (users != null ? users.size() : 0) + " users in database");
            if (users != null && !users.isEmpty()) {
                System.out.println("First user: " + users.get(0).getUsername() + " (" + users.get(0).getRole() + ")");
            }
            // Ensure we always return a list, never null
            if (users == null) {
                users = new java.util.ArrayList<>();
            }
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(users);
        } catch (Exception e) {
            System.err.println("Error in listUsers: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new java.util.ArrayList<>());
        }
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<User> getUser(@PathVariable long id) {
        return bayportService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/users")
    public User createUser(@RequestBody User u){ return bayportService.saveUser(u); }
    
    // New OTP-based user creation endpoints
    @PostMapping("/users/send-otp")
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
            mfaService.sendOtpByEmail(email);
            return ResponseEntity.ok(Map.of("status", "OTP_SENT", "message", "OTP sent to email"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send OTP: " + e.getMessage()));
        }
    }
    
    @PostMapping("/users/verify-otp-create")
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
        
        // Verify OTP by email
        if (!mfaService.verifyOtpByEmail(email, otp)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired OTP"));
        }
        
        // Create user
        try {
            User newUser = new User();
            newUser.setName(name);
            newUser.setFullName(name);
            newUser.setUsername(username);
            newUser.setEmail(email);
            newUser.setRole(role);
            newUser.setPassword(password); // Will be hashed by saveUser
            newUser.setMfaEnabled(true); // Enable MFA for all new users
            
            User created = bayportService.saveUser(newUser);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to create user: " + e.getMessage()));
        }
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<User> updateUser(@PathVariable long id, @RequestBody User u){
        if (bayportService.getUserById(id).isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(bayportService.updateUser(id, u));
    }

    @GetMapping("/users/{id}/password")
    public ResponseEntity<?> getUserPassword(@PathVariable long id) {
        return bayportService.getUserById(id)
                .map(user -> {
                    String displayPassword = user.getDisplayPassword();
                    if (displayPassword == null || displayPassword.isEmpty()) {
                        return ResponseEntity.ok(Map.of("password", "", "hasPassword", false));
                    }
                    return ResponseEntity.ok(Map.of("password", displayPassword, "hasPassword", true));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/users/{id}/send-edit-otp")
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
                    } catch (Exception e) {
                        return ResponseEntity.status(500)
                                .body(Map.of("error", "Failed to send OTP: " + e.getMessage()));
        }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/users/{id}/edit-credentials")
    public ResponseEntity<?> editCredentials(@PathVariable long id,
                                              @RequestBody Map<String, Object> request) {
        String otp = (String) request.get("otp");
        String username = (String) request.get("username");
        String email = (String) request.get("email");
        String password = (String) request.get("password");
        
        return bayportService.getUserById(id)
                .map(user -> {
                    // Verify OTP
                    if (otp == null || otp.trim().isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "OTP is required"));
                    }
                    
                    if (!mfaService.verifyCode(user, otp)) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "Invalid or expired OTP"));
                    }
                    
                    // Update credentials
                    try {
                        boolean updated = false;
                        if (username != null && !username.trim().isEmpty() && !username.equals(user.getUsername())) {
                            user.setUsername(username);
                            updated = true;
                        }
                        if (email != null && !email.trim().isEmpty() && !email.equals(user.getEmail())) {
                            // Check if email is already in use by another user
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
                            return ResponseEntity.ok(Map.of("success", true, "message", "Credentials updated successfully"));
                        } else {
            return ResponseEntity.badRequest()
                                    .body(Map.of("error", "No changes provided"));
                        }
        } catch (Exception e) {
            return ResponseEntity.status(500)
                                .body(Map.of("error", "Failed to update credentials: " + e.getMessage()));
        }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable long id){
        if (bayportService.getUserById(id).isEmpty()) return ResponseEntity.notFound().build();
        bayportService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    /* --------- Reports & Ops --------- */
    @GetMapping("/ops/log")
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
    public ResponseEntity<List<PosSaleHistoryDto>> reportsPosSalesRecent(
            @RequestParam(name = "limit", defaultValue = "40") int limit) {
        return ResponseEntity.ok(posService.recentPosSales(limit));
    }

    @GetMapping("/reports/summary")
    public ResponseEntity<?> summary(
            @RequestParam(name = "period", defaultValue = "day") String period,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to
    ){
        // Check if user is admin
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access Denied", "message", "Authentication required"));
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
            // User is admin, continue
        } else {
            // Fallback: Check user entity
            String username = auth.getName();
            java.util.Optional<com.bayport.entity.User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access Denied", "message", "User not found"));
            }
            com.bayport.entity.User user = userOpt.get();
            
            // Check legacy role field
            boolean isAdmin = false;
            if (user.getRole() != null) {
                String role = user.getRole().toLowerCase();
                isAdmin = "admin".equals(role) || "administrator".equals(role);
            }
            
            // Check roles set
            if (!isAdmin) {
                isAdmin = user.getRoles().stream().anyMatch(r -> {
                    String roleName = r.getName().toUpperCase();
                    return "ROLE_ADMIN".equals(roleName) || 
                           "ADMIN".equals(roleName) ||
                           "ADMINISTRATOR".equals(roleName);
                });
            }
            
            if (!isAdmin) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access Denied", "message", "Only administrators can access reports"));
            }
        }
        
        try {
            LocalDate today = LocalDate.now();
            LocalDate start;
            LocalDate end;

            switch (period) {
                case "day" -> { start = today; end = today; }
                case "week" -> { start = today.minusDays(6); end = today; }
                case "month" -> { start = today.withDayOfMonth(1); end = today; }
                case "custom" -> {
                    start = LocalDate.parse(Objects.requireNonNull(from, "from is required for custom period"));
                    end   = LocalDate.parse(Objects.requireNonNull(to, "to is required for custom period"));
                }
                default -> {
                    start = today;
                    end = today;
                }
            }

            return ResponseEntity.ok(reportService.summarize(start, end, period));
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
        return ResponseEntity.ok(body);
    }

    @GetMapping(value = "/prescriptions/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPrescriptionPdf(@PathVariable long id,
                                                          @RequestParam(name = "group", required = false) String groupKey) {
        return bayportService.getPrescriptionById(id)
                .map(firstPrescription -> {
                    List<Prescription> prescriptions;
                    if (groupKey != null && !groupKey.isBlank()) {
                        // Fetch all prescriptions in the group (same pet, date, prescriber)
                        String[] parts = groupKey.split("_");
                        if (parts.length >= 3) {
                            Long petId = Long.parseLong(parts[0]);
                            String date = parts[1];
                            String prescriber = parts[2];
                            prescriptions = bayportService.getAllPrescriptions().stream()
                                    .filter(p -> p.getPetId() != null && p.getPetId().equals(petId))
                                    .filter(p -> p.getDate() != null && p.getDate().toString().equals(date))
                                    .filter(p -> prescriber.equals(p.getPrescriber()))
                                    .collect(java.util.stream.Collectors.toList());
                        } else {
                            prescriptions = java.util.Collections.singletonList(firstPrescription);
                        }
                    } else {
                        // Single prescription - try to find group automatically
                        prescriptions = bayportService.getAllPrescriptions().stream()
                                .filter(p -> p.getPetId() != null && p.getPetId().equals(firstPrescription.getPetId()))
                                .filter(p -> p.getDate() != null && firstPrescription.getDate() != null && 
                                           p.getDate().equals(firstPrescription.getDate()))
                                .filter(p -> firstPrescription.getPrescriber() != null && 
                                           firstPrescription.getPrescriber().equals(p.getPrescriber()))
                                .collect(java.util.stream.Collectors.toList());
                        if (prescriptions.isEmpty()) {
                            prescriptions = java.util.Collections.singletonList(firstPrescription);
                        }
                    }
                    
                    byte[] pdf = pdfService.buildPrescriptionPdf(prescriptions);
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=Prescription_" + firstPrescription.getPet() + "_" + firstPrescription.getDate() + ".pdf")
                            .body(pdf);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/reports/summary/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadReportPdf(
            @RequestParam(name = "period", defaultValue = "day") String period,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "preparedBy", required = false) String preparedBy
    ) {
        // Check if user is admin
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        
        // Check Spring Security authorities first (most reliable)
        boolean hasAdminAuthority = auth.getAuthorities().stream()
                .anyMatch(a -> {
                    String authority = a.getAuthority();
                    return "ROLE_ADMIN".equalsIgnoreCase(authority) || 
                           "ADMIN".equalsIgnoreCase(authority) ||
                           "ADMINISTRATOR".equalsIgnoreCase(authority);
                });
        if (!hasAdminAuthority) {
            // Fallback: Check user entity
            String username = auth.getName();
            java.util.Optional<com.bayport.entity.User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
            }
            com.bayport.entity.User user = userOpt.get();
            
            // Check legacy role field
            boolean isAdmin = false;
            if (user.getRole() != null) {
                String role = user.getRole().toLowerCase();
                isAdmin = "admin".equals(role) || "administrator".equals(role);
            }
            
            // Check roles set
            if (!isAdmin) {
                isAdmin = user.getRoles().stream().anyMatch(r -> {
                    String roleName = r.getName().toUpperCase();
                    return "ROLE_ADMIN".equals(roleName) || 
                           "ADMIN".equals(roleName) ||
                           "ADMINISTRATOR".equals(roleName);
                });
            }
            
            if (!isAdmin) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
            }
        }
        
        try {
            LocalDate today = LocalDate.now();
            LocalDate start;
            LocalDate end;

            switch (period) {
                case "day" -> { start = today; end = today; }
                case "week" -> { start = today.minusDays(6); end = today; }
                case "month" -> { start = today.withDayOfMonth(1); end = today; }
                case "custom" -> {
                    start = LocalDate.parse(Objects.requireNonNull(from, "from is required for custom period"));
                    end   = LocalDate.parse(Objects.requireNonNull(to, "to is required for custom period"));
                }
                default -> {
                    start = today;
                    end = today;
                }
            }

            ReportSummary summary = reportService.summarize(start, end, period);
            byte[] pdf = pdfService.buildSummaryPdf(summary, preparedBy);
            
            String filename = String.format("Bayport_Summary_%s_%s.pdf", 
                    start.toString(), end.toString());
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/admin/reset-data")
    public ResponseEntity<Map<String, Object>> resetData(@RequestBody AdminResetRequest request) {
        bayportService.resetSystemData(request.username(), request.password());
        return ResponseEntity.ok(Map.of("success", true));
    }

    public record AdminResetRequest(String username, String password) {}
}
