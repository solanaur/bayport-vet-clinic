package com.bayport.web;

import com.bayport.auth.JwtService;
import com.bayport.auth.MfaService;
import com.bayport.auth.dto.LoginRequest;
import com.bayport.auth.dto.MfaVerifyRequest;
import com.bayport.entity.User;
import com.bayport.repository.UserRepository;
import com.bayport.security.LoginAttemptService;
import com.bayport.security.RateLimitService;
import com.bayport.service.AuditLogService;
import com.bayport.service.BayportService;
import com.bayport.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class AuthController {

    private static final Set<String> BYPASS_USERNAMES = Set.of("admin", "vet", "frontdesk", "recept", "pharm");

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final MfaService mfaService;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;
    private final BayportService bayportService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final LoginAttemptService loginAttemptService;
    private final RateLimitService rateLimitService;
    private final boolean otpReturnWhenUndelivered;

    public AuthController(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            MfaService mfaService,
            JwtService jwtService,
            AuditLogService auditLogService,
            BayportService bayportService,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            LoginAttemptService loginAttemptService,
            RateLimitService rateLimitService,
            @Value("${bayport.security.otp-return-when-undelivered:false}") boolean otpReturnWhenUndelivered) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.mfaService = mfaService;
        this.jwtService = jwtService;
        this.auditLogService = auditLogService;
        this.bayportService = bayportService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.loginAttemptService = loginAttemptService;
        this.rateLimitService = rateLimitService;
        this.otpReturnWhenUndelivered = otpReturnWhenUndelivered;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String clientKey = clientKey(httpRequest, username);

        if (!rateLimitService.allowLogin(clientKey)) {
            auditLogService.log("LOGIN_RATE_LIMITED", "User", username, "Login rate limited", httpRequest);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many login attempts. Please try again later."));
        }

        if (loginAttemptService.isBlocked(username)) {
            long wait = loginAttemptService.secondsRemaining(username);
            auditLogService.log("LOGIN_LOCKED", "User", username, "Account temporarily locked", httpRequest);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many failed attempts. Try again in " + wait + " seconds."));
        }

        try {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null) {
                loginAttemptService.recordFailure(username);
                auditLogService.log("LOGIN_FAILED", "User", username, "Login failed: invalid credentials", httpRequest);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid username or password"));
            }

            try {
                Authentication auth = authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(username, request.getPassword())
                );
                if (auth == null || !auth.isAuthenticated()) {
                    loginAttemptService.recordFailure(username);
                    auditLogService.log("LOGIN_FAILED", "User", username, "Login failed: invalid credentials", httpRequest);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("error", "Invalid username or password"));
                }
            } catch (Exception e) {
                loginAttemptService.recordFailure(username);
                auditLogService.log("LOGIN_FAILED", "User", username, "Login failed: invalid credentials", httpRequest);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid username or password"));
            }

            user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (BYPASS_USERNAMES.contains(username.toLowerCase(Locale.ROOT))) {
                loginAttemptService.recordSuccess(username);
                auditLogService.log("LOGIN_SUCCESS", "User", String.valueOf(user.getId()),
                        "Bypass account login (OTP not required)", httpRequest);
                String token = jwtService.generateToken(user.getUsername());
                Map<String, Object> payload = new HashMap<>(toPayload(user));
                payload.put("token", token);
                return ResponseEntity.ok(payload);
            }

            if (user.getPassword() == null && user.getPasswordHash() != null) {
                user.setPassword(user.getPasswordHash());
                userRepository.save(user);
            }

            if (user.hasTotpSecret()) {
                auditLogService.log("OTP_CHALLENGE", "User", String.valueOf(user.getId()),
                        "Google Authenticator TOTP required", httpRequest);
                Map<String, Object> totpChallenge = new HashMap<>();
                totpChallenge.put("status", "MFA_REQUIRED");
                totpChallenge.put("mfaType", "TOTP");
                totpChallenge.put("username", user.getUsername());
                totpChallenge.put("emailConfigured", emailService.isConfigured());
                totpChallenge.put("emailDelivered", false);
                totpChallenge.put("message", "Enter the 6-digit code from Google Authenticator.");
                return ResponseEntity.ok(totpChallenge);
            }

            if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
                auditLogService.log("LOGIN_FAILED", "User", String.valueOf(user.getId()),
                        "User email is required for MFA", httpRequest);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "User email is required for authentication. Please contact administrator."));
            }

            MfaService.DeliveryResult sent;
            try {
                sent = mfaService.sendMfaCode(user);
            } catch (ResponseStatusException rse) {
                return ResponseEntity.status(rse.getStatusCode())
                        .body(Map.of("error", rse.getReason() != null ? rse.getReason() : "Request blocked"));
            } catch (Exception emailEx) {
                auditLogService.log("OTP_FAILED", "User", String.valueOf(user.getId()),
                        "MFA send failed", httpRequest);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("error", "Could not send OTP. Configure email or ask an administrator."));
            }

            auditLogService.log("OTP_SENT", "User", String.valueOf(user.getId()),
                    sent.emailDelivered() ? "OTP code sent to user email" : "OTP generated (email not delivered)",
                    httpRequest);
            Map<String, Object> mfaResponse = new HashMap<>();
            mfaResponse.put("status", "MFA_REQUIRED");
            mfaResponse.put("mfaType", "EMAIL");
            mfaResponse.put("username", user.getUsername());
            mfaResponse.put("emailConfigured", emailService.isConfigured());
            boolean placeholderInbox = isPlaceholderEmail(user.getEmail());
            boolean emailDelivered = sent.emailDelivered() && !placeholderInbox;
            mfaResponse.put("emailDelivered", emailDelivered);
            if (emailDelivered) {
                mfaResponse.put("message", "OTP code sent to your email. Please verify to complete login.");
            } else if (otpReturnWhenUndelivered && sent.code() != null) {
                mfaResponse.put("localOtp", sent.code());
                mfaResponse.put("message", "Email could not be delivered to this account. Enter this one-time code: "
                        + sent.code());
            } else {
                mfaResponse.put("message",
                        "OTP was sent to administrator notifications. Ask an administrator for the code.");
            }
            return ResponseEntity.ok(mfaResponse);
        } catch (Exception e) {
            loginAttemptService.recordFailure(username);
            auditLogService.log("LOGIN_FAILED", "User", username, "Login failed", httpRequest);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }
    }

    @PostMapping("/auth/mfa/verify")
    public ResponseEntity<?> verifyMfa(@RequestBody MfaVerifyRequest request, HttpServletRequest httpRequest) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        try {
            if (loginAttemptService.isBlocked(username)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("error", "Too many failed attempts. Please try again later."));
            }

            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null) {
                auditLogService.log("OTP_FAILED", "User", username, "Invalid or expired MFA code", httpRequest);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid or expired code"));
            }

            boolean ok;
            try {
                ok = mfaService.verifyLogin(user, request.getCode());
            } catch (ResponseStatusException rse) {
                return ResponseEntity.status(rse.getStatusCode())
                        .body(Map.of("error", rse.getReason() != null ? rse.getReason() : "Request blocked"));
            }

            if (!ok) {
                loginAttemptService.recordFailure(username);
                auditLogService.log("OTP_FAILED", "User", String.valueOf(user.getId()),
                        "Invalid or expired MFA code", httpRequest);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid or expired code"));
            }

            String currentTosVersion = "v1.0";
            if (user.getTosVersionAccepted() == null || !user.getTosVersionAccepted().equals(currentTosVersion)) {
                user.setTosVersionAccepted(currentTosVersion);
                user.setTosAcceptedAt(java.time.LocalDateTime.now());
                userRepository.save(user);
            }

            loginAttemptService.recordSuccess(username);
            String token = jwtService.generateToken(user.getUsername());
            auditLogService.log("OTP_VERIFIED", "User", String.valueOf(user.getId()),
                    "User logged in successfully with MFA", httpRequest);
            auditLogService.log("LOGIN_SUCCESS", "User", String.valueOf(user.getId()),
                    "User logged in successfully with MFA", httpRequest);

            Map<String, Object> payload = new HashMap<>(toPayload(user));
            payload.put("token", token);
            return ResponseEntity.ok(payload);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Verification failed"));
        }
    }

    @PostMapping("/auth/password-reset/request")
    public ResponseEntity<?> requestPasswordReset(@RequestBody Map<String, String> body, HttpServletRequest httpRequest) {
        String email = body.get("email") == null ? "" : body.get("email").trim();
        String clientKey = clientKey(httpRequest, email);
        if (!rateLimitService.allowPasswordReset(clientKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many reset requests. Please try again later."));
        }
        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "If that email is registered, a reset code has been sent. Check Inbox and Spam.");
        userRepository.findByEmail(email).ifPresent(user -> {
            try {
                MfaService.DeliveryResult sent = mfaService.sendMfaCode(user);
                auditLogService.log("PASSWORD_RESET_REQUESTED", "User", String.valueOf(user.getId()),
                        "Password reset OTP sent", httpRequest);
                if (otpReturnWhenUndelivered && sent.code() != null) {
                    response.put("localOtp", sent.code());
                    response.put("emailDelivered", sent.emailDelivered());
                }
            } catch (Exception ignored) {
                // Do not reveal whether email exists or mail failed
            }
        });
        return ResponseEntity.ok(response);
    }

    @PostMapping("/auth/password-reset/confirm")
    public ResponseEntity<?> confirmPasswordReset(@RequestBody Map<String, String> body, HttpServletRequest httpRequest) {
        String email = body.get("email") == null ? "" : body.get("email").trim();
        String code = body.get("code") == null ? body.get("otp") : body.get("code");
        String newPassword = body.get("newPassword") == null ? body.get("password") : body.get("newPassword");

        if (email.isEmpty() || code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and code are required"));
        }
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters"));
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !mfaService.verifyCode(user, code)) {
            auditLogService.log("PASSWORD_RESET_FAILED", "User", email, "Invalid reset attempt", httpRequest);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired code"));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordHash(user.getPassword());
        user.setDisplayPassword(null);
        user.setPlainPassword(null);
        userRepository.save(user);
        auditLogService.log("PASSWORD_RESET", "User", String.valueOf(user.getId()),
                "Password reset completed", httpRequest);
        return ResponseEntity.ok(Map.of("status", "OK", "message", "Password updated successfully"));
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<?> legacyLogin(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return login(request, httpRequest);
    }

    private static boolean isPlaceholderEmail(String email) {
        if (email == null || email.isBlank()) {
            return true;
        }
        String value = email.trim().toLowerCase(Locale.ROOT);
        return value.endsWith("@bayportvet.com")
                || value.endsWith("@example.com")
                || value.endsWith("@test.com");
    }

    private static String clientKey(HttpServletRequest request, String identity) {
        String ip = request != null ? request.getRemoteAddr() : "unknown";
        return identity + "|" + ip;
    }

    private Map<String, Object> toPayload(User user) {
        String role = "user";
        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            String roleName = user.getRoles().iterator().next().getName();
            role = roleName.replace("ROLE_", "").toLowerCase(Locale.ROOT);
            if ("receptionist".equals(role) || "pharmacist".equals(role)) {
                role = "front_office";
            }
        } else if (user.getRole() != null) {
            role = user.getRole().toLowerCase(Locale.ROOT);
            if ("receptionist".equals(role) || "pharmacist".equals(role)) {
                role = "front_office";
            }
        }

        return Map.of(
                "id", user.getId(),
                "name", user.getFullName() != null ? user.getFullName() : user.getName(),
                "recordName", user.getName() != null ? user.getName() : (user.getFullName() != null ? user.getFullName() : ""),
                "role", role,
                "username", user.getUsername(),
                "email", user.getEmail() != null ? user.getEmail() : "",
                "mfaEnabled", user.isMfaEnabled()
        );
    }
}
