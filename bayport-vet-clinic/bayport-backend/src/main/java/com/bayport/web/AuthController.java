package com.bayport.web;

import com.bayport.auth.JwtService;
import com.bayport.auth.MfaService;
import com.bayport.auth.dto.LoginRequest;
import com.bayport.auth.dto.MfaVerifyRequest;
import com.bayport.entity.User;
import com.bayport.repository.UserRepository;
import com.bayport.service.AuditLogService;
import com.bayport.service.BayportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final MfaService mfaService;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;
    private final BayportService bayportService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            MfaService mfaService,
            JwtService jwtService,
            AuditLogService auditLogService,
            BayportService bayportService,
            PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.mfaService = mfaService;
        this.jwtService = jwtService;
        this.auditLogService = auditLogService;
        this.bayportService = bayportService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Special handling for admin and default accounts - bypass OTP requirement
            String requestUsername = request.getUsername();
            String usernameLower = requestUsername.toLowerCase();
            boolean isDefaultAccount = usernameLower.equals("admin") || usernameLower.equals("vet") ||
                                     usernameLower.equals("recept") || usernameLower.equals("pharm") ||
                                     usernameLower.equals("frontdesk");
            
            // Check both the request username and the user's role
            boolean isAdminRole = "admin".equalsIgnoreCase(user.getRole()) || 
                                 user.getRoles().stream().anyMatch(r -> "ROLE_ADMIN".equalsIgnoreCase(r.getName()));
            
            if (isDefaultAccount || isAdminRole) {
                // Use Spring Security authentication for password verification
                try {
                    Authentication auth = authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
                    );
                    
                    if (auth == null || !auth.isAuthenticated()) {
                        auditLogService.log("LOGIN_FAILED", "User", request.getUsername(), 
                            "Login failed: Invalid credentials", httpRequest);
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(Map.of("error", "Invalid username or password"));
                    }
                    
                    // Reload user to get latest data
                    user = userRepository.findByUsername(request.getUsername())
                            .orElseThrow(() -> new RuntimeException("User not found"));
                    
                    // Default accounts (admin, vet, recept, pharm) bypass all checks - no TOS, no OTP, no restrictions
                    String logMessage = isDefaultAccount ? 
                        String.format("%s logged in (bypass all authentication)", user.getUsername()) :
                        "Admin logged in (bypass all authentication)";
                    auditLogService.log("LOGIN", "User", String.valueOf(user.getId()), logMessage, httpRequest);
                    
                    // Generate JWT token for bypass accounts
                    String token = jwtService.generateToken(user.getUsername());
                    
                    // Return response in format expected by frontend
                    Map<String, Object> payload = toPayload(user);
                    payload = new java.util.HashMap<>(payload);
                    payload.put("token", token); // Include JWT token in response
                    return ResponseEntity.ok(payload);
                } catch (Exception e) {
                    auditLogService.log("LOGIN_FAILED", "User", request.getUsername(), 
                        "Login failed: " + e.getMessage(), httpRequest);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("error", "Invalid username or password"));
                }
            }

            // For non-admin users, use Spring Security authentication
            // First, ensure password is set (migrate from passwordHash if needed)
            if (user.getPassword() == null && user.getPasswordHash() != null) {
                user.setPassword(user.getPasswordHash());
                userRepository.save(user);
            }

            // Authenticate user
            Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            // ALWAYS require OTP verification for all users (MFA requirement)
            // Check if user has email for OTP
            if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
                auditLogService.log("LOGIN_FAILED", "User", String.valueOf(user.getId()), 
                    "User email is required for MFA", httpRequest);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "User email is required for authentication. Please contact administrator."));
            }
            
            // Send OTP to user's email
            mfaService.sendMfaCode(user);
            auditLogService.log("LOGIN_MFA_REQUIRED", "User", String.valueOf(user.getId()), 
                "OTP code sent to user email", httpRequest);
            return ResponseEntity.ok(Map.of(
                "status", "MFA_REQUIRED",
                "message", "OTP code sent to your email. Please verify to complete login.",
                "username", user.getUsername()
            ));
        } catch (Exception e) {
            auditLogService.log("LOGIN_FAILED", "User", request.getUsername(), 
                "Login failed: " + e.getMessage(), httpRequest);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }
    }

    @PostMapping("/auth/mfa/verify")
    public ResponseEntity<?> verifyMfa(@RequestBody MfaVerifyRequest request, HttpServletRequest httpRequest) {
        try {
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (!mfaService.verifyCode(user, request.getCode())) {
                auditLogService.log("MFA_VERIFY_FAILED", "User", String.valueOf(user.getId()), 
                    "Invalid or expired MFA code", httpRequest);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid or expired code"));
            }

            // TOS is now handled on frontend via checkbox, but we still update the database
            String currentTosVersion = "v1.0";
            if (user.getTosVersionAccepted() == null || !user.getTosVersionAccepted().equals(currentTosVersion)) {
                user.setTosVersionAccepted(currentTosVersion);
                user.setTosAcceptedAt(java.time.LocalDateTime.now());
                userRepository.save(user);
            }

            String token = jwtService.generateToken(user.getUsername());
            
            auditLogService.log("LOGIN", "User", String.valueOf(user.getId()), 
                "User logged in successfully with MFA", httpRequest);

            // Return response in format expected by frontend (flat structure with role at top level)
            Map<String, Object> payload = toPayload(user);
            payload = new java.util.HashMap<>(payload);
            payload.put("token", token); // Include JWT token in response
            return ResponseEntity.ok(payload);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Verification failed: " + e.getMessage()));
        }
    }

    // Legacy endpoint for backward compatibility - handles /api/auth/login
    @PostMapping("/api/auth/login")
    public ResponseEntity<?> legacyLogin(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return login(request, httpRequest);
    }

    private Map<String, Object> toPayload(User user) {
        // Get role from roles set or fallback to legacy role field
        String role = "user";
        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            String roleName = user.getRoles().iterator().next().getName();
            role = roleName.replace("ROLE_", "").toLowerCase();
            if ("receptionist".equals(role) || "pharmacist".equals(role)) {
                role = "front_office";
            }
        } else if (user.getRole() != null) {
            role = user.getRole().toLowerCase();
            if ("receptionist".equals(role) || "pharmacist".equals(role)) {
                role = "front_office";
            }
        }
        
        // Return flat structure expected by frontend (not nested in "user" object)
        return Map.of(
                "id", user.getId(),
                "name", user.getFullName() != null ? user.getFullName() : user.getName(),
                "role", role,
                "username", user.getUsername(),
                "email", user.getEmail() != null ? user.getEmail() : "",
                "mfaEnabled", user.isMfaEnabled()
        );
    }
}

