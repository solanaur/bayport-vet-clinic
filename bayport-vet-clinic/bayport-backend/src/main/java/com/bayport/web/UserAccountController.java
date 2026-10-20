package com.bayport.web;

import com.bayport.auth.RecoveryCodeService;
import com.bayport.auth.TotpEnrollmentService;
import com.bayport.entity.User;
import com.bayport.repository.UserRepository;
import com.bayport.security.PasswordPolicy;
import com.bayport.security.TemporaryAdminService;
import com.bayport.service.AuditLogService;
import com.bayport.service.BayportService;
import com.bayport.service.EmailService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class UserAccountController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserAccountController.class);
    private static final Set<String> ALLOWED_ROLES = Set.of(
            "admin", "vet", "veterinarian", "front_office", "receptionist", "pharmacist", "staff");

    private final BayportService bayportService;
    private final com.bayport.auth.MfaService mfaService;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final TotpEnrollmentService totpEnrollmentService;
    private final RecoveryCodeService recoveryCodeService;
    private final TemporaryAdminService temporaryAdminService;

    public UserAccountController(
            BayportService bayportService,
            com.bayport.auth.MfaService mfaService,
            UserRepository userRepository,
            EmailService emailService,
            AuditLogService auditLogService,
            TotpEnrollmentService totpEnrollmentService,
            RecoveryCodeService recoveryCodeService,
            TemporaryAdminService temporaryAdminService
    ) {
        this.bayportService = bayportService;
        this.mfaService = mfaService;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
        this.totpEnrollmentService = totpEnrollmentService;
        this.recoveryCodeService = recoveryCodeService;
        this.temporaryAdminService = temporaryAdminService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<User>> listUsers() {
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
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
    public User createUser(@RequestBody User user) {
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            PasswordPolicy.require(user.getPassword());
        }
        User created = bayportService.saveUser(user);
        temporaryAdminService.retireFactoryAccounts();
        return created;
    }

    @PostMapping("/users/send-otp")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> sendUserCreationOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
        }
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is already registered"));
        }
        try {
            com.bayport.auth.MfaService.DeliveryResult sent = mfaService.sendOtpByEmail(email);
            Map<String, Object> body = new HashMap<>();
            body.put("status", "OTP_SENT");
            body.put("emailDelivered", sent.emailDelivered());
            body.put("from", emailService.getConfiguredFrom());
            if (sent.emailDelivered()) {
                String from = emailService.getConfiguredFrom();
                String hint = "A 6-digit code was emailed from " + (from == null || from.isBlank() ? "the clinic mailbox" : from)
                        + ". Check Inbox and Spam for " + email.trim() + ".";
                body.put("message", hint);
            } else {
                body.put("message", "The verification email could not be sent. Configure email and try again.");
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
        try {
            PasswordPolicy.require(password);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }

        String normalizedRole = role.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_ROLES.contains(normalizedRole)) {
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
            Object phone = request.get("phone");
            if (phone != null && !String.valueOf(phone).isBlank()) {
                newUser.setPhone(String.valueOf(phone).trim());
            }
            newUser.setRole(role);
            newUser.setPassword(password);
            newUser.setMfaEnabled(true);
            newUser.setTotpPrompted(false);
            newUser.setTotpConfirmed(false);
            newUser.setMustChangePassword(false);

            User created = bayportService.saveUser(newUser);
            TotpEnrollmentService.EncryptedEnrollment enrollment = totpEnrollmentService.enroll(created.getUsername());
            created.setTotpSecret(enrollment.encryptedSecret());
            created.setMfaEnabled(true);
            created.setTotpPrompted(false);
            created.setTotpConfirmed(false);
            created = userRepository.save(created);

            Map<String, Object> body = new HashMap<>();
            body.put("id", created.getId());
            body.put("name", created.getFullName() != null ? created.getFullName() : created.getName());
            body.put("username", created.getUsername());
            body.put("email", created.getEmail());
            body.put("role", created.getRole());
            body.put("mfaEnabled", true);
            body.put("mfaType", "TOTP");
            body.put("totpSetupOnFirstLogin", true);
            auditLogService.log("USER_CREATED", "User", String.valueOf(created.getId()), "User created", null);
            if (temporaryAdminService.retireFactoryAccounts()) {
                body.put("signOutRequired", true);
                body.put("message", "The temporary admin login has been removed. Sign in with the administrator account you just created.");
            }
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to create user"));
        }
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> updateUser(@PathVariable long id, @RequestBody User user) {
        Optional<User> existingOpt = bayportService.getUserById(id);
        if (existingOpt.isEmpty()) return ResponseEntity.notFound().build();
        String oldRole = existingOpt.get().getRole();
        User updated = bayportService.updateUser(id, user);
        if (oldRole != null && updated.getRole() != null && !oldRole.equalsIgnoreCase(updated.getRole())) {
            auditLogService.log("ROLE_CHANGED", "User", String.valueOf(id), "Role changed", null);
        }
        auditLogService.log("USER_UPDATED", "User", String.valueOf(id), "User updated", null);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/users/{id}/reset-mfa")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> resetMfa(@PathVariable long id, HttpServletRequest request) {
        return bayportService.getUserById(id)
                .map(user -> {
                    user.setTotpSecret(null);
                    user.setTotpConfirmed(false);
                    user.setTotpPrompted(false);
                    user.setTotpLastStep(null);
                    user.setMfaEnabled(true);
                    user.bumpTokenVersion();
                    userRepository.save(user);
                    recoveryCodeService.deleteAll(user.getId());
                    auditLogService.log("MFA_RESET", "User", String.valueOf(id),
                            "Administrator reset MFA", request);
                    return ResponseEntity.ok(Map.of(
                            "status", "OK",
                            "message", "MFA reset. The user must set up Google Authenticator on the next sign-in."
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/users/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getUserPassword(@PathVariable long id) {
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
    public ResponseEntity<?> editCredentials(@PathVariable long id, @RequestBody Map<String, Object> request) {
        String otp = (String) request.get("otp");
        String username = (String) request.get("username");
        String email = (String) request.get("email");
        String password = (String) request.get("password");

        return bayportService.getUserById(id)
                .map(user -> {
                    if (otp == null || otp.trim().isEmpty()) {
                        return ResponseEntity.badRequest().body(Map.of("error", "OTP is required"));
                    }
                    if (!mfaService.verifyCode(user, otp)) {
                        return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired OTP"));
                    }
                    try {
                        boolean updated = false;
                        if (username != null && !username.trim().isEmpty() && !username.equals(user.getUsername())) {
                            user.setUsername(username);
                            updated = true;
                        }
                        if (email != null && !email.trim().isEmpty() && !email.equals(user.getEmail())) {
                            Optional<User> existing = userRepository.findByEmail(email);
                            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                                return ResponseEntity.badRequest()
                                        .body(Map.of("error", "Email is already registered to another user"));
                            }
                            user.setEmail(email);
                            updated = true;
                        }
                        if (password != null && !password.trim().isEmpty()) {
                            PasswordPolicy.require(password);
                            user.setPassword(password);
                            user.setMustChangePassword(false);
                            updated = true;
                        }
                        if (updated) {
                            bayportService.updateUser(id, user);
                            auditLogService.log("PASSWORD_CHANGED", "User", String.valueOf(id),
                                    "Credentials updated via admin OTP flow", null);
                            return ResponseEntity.ok(Map.of("success", true, "message", "Credentials updated successfully"));
                        }
                        return ResponseEntity.badRequest().body(Map.of("error", "No changes provided"));
                    } catch (IllegalArgumentException ex) {
                        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
                    } catch (Exception e) {
                        return ResponseEntity.status(500).body(Map.of("error", "Failed to update credentials"));
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable long id) {
        if (bayportService.getUserById(id).isEmpty()) return ResponseEntity.notFound().build();
        bayportService.deleteUser(id);
        auditLogService.log("USER_DISABLED", "User", String.valueOf(id), "User deleted", null);
        return ResponseEntity.noContent().build();
    }
}
