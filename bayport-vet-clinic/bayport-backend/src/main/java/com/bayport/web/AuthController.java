package com.bayport.web;

import com.bayport.auth.JwtService;
import com.bayport.auth.MfaService;
import com.bayport.auth.RecoveryCodeService;
import com.bayport.auth.TotpEnrollmentService;
import com.bayport.auth.TotpService;
import com.bayport.auth.dto.LoginRequest;
import com.bayport.auth.dto.MfaVerifyRequest;
import com.bayport.entity.User;
import com.bayport.repository.UserRepository;
import com.bayport.security.ClientIpResolver;
import com.bayport.security.LoginAttemptService;
import com.bayport.security.LoginGate;
import com.bayport.security.PasswordPolicy;
import com.bayport.security.RateLimitService;
import com.bayport.security.TemporaryAdminService;
import com.bayport.security.TokenScopes;
import com.bayport.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final MfaService mfaService;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;
    private final TotpEnrollmentService totpEnrollmentService;
    private final RecoveryCodeService recoveryCodeService;
    private final LoginAttemptService loginAttemptService;
    private final RateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;
    private final TemporaryAdminService temporaryAdminService;

    public AuthController(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            MfaService mfaService,
            JwtService jwtService,
            AuditLogService auditLogService,
            PasswordEncoder passwordEncoder,
            TotpEnrollmentService totpEnrollmentService,
            RecoveryCodeService recoveryCodeService,
            LoginAttemptService loginAttemptService,
            RateLimitService rateLimitService,
            ClientIpResolver clientIpResolver,
            TemporaryAdminService temporaryAdminService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.mfaService = mfaService;
        this.jwtService = jwtService;
        this.auditLogService = auditLogService;
        this.passwordEncoder = passwordEncoder;
        this.totpEnrollmentService = totpEnrollmentService;
        this.recoveryCodeService = recoveryCodeService;
        this.loginAttemptService = loginAttemptService;
        this.rateLimitService = rateLimitService;
        this.clientIpResolver = clientIpResolver;
        this.temporaryAdminService = temporaryAdminService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String ip = clientIpResolver.resolve(httpRequest);
        String clientKey = username + "|" + ip;

        if (!rateLimitService.allowLogin(clientKey)) {
            auditLogService.log("LOGIN_RATE_LIMITED", "User", username, "Login rate limited", httpRequest);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many login attempts. Please try again later."));
        }

        if (loginAttemptService.isBlocked(username, ip)) {
            long wait = loginAttemptService.secondsRemaining(username, ip);
            auditLogService.log("LOGIN_LOCKED", "User", username, "Account temporarily locked for this address", httpRequest);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many failed attempts. Try again in " + wait + " seconds."));
        }

        try {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null || !passwordAccepted(username, request.getPassword())) {
                loginAttemptService.recordFailure(username, ip);
                auditLogService.log("LOGIN_FAILED", "User", username, "Login failed: invalid credentials", httpRequest);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid username or password"));
            }

            user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            loginAttemptService.recordSuccess(username, ip);

            if (temporaryAdminService.bypassLoginAllowed(user.getUsername(), request.getPassword())) {
                auditLogService.log("TEMP_ADMIN_LOGIN", "User", String.valueOf(user.getId()),
                        "Temporary administrator signed in to create a real account", httpRequest);
                return ResponseEntity.ok(sessionPayload(user));
            }

            if (LoginGate.mustReplacePassword(request.getPassword(), user.isMustChangePassword())) {
                auditLogService.log("PASSWORD_CHANGE_REQUIRED", "User", String.valueOf(user.getId()),
                        "Password must be replaced before sign-in", httpRequest);
                Map<String, Object> change = new HashMap<>();
                change.put("status", "PASSWORD_CHANGE_REQUIRED");
                change.put("username", user.getUsername());
                change.put("message", "Choose a new password of at least 10 characters before continuing.");
                return ResponseEntity.ok(change);
            }

            if (user.getPassword() == null && user.getPasswordHash() != null) {
                user.setPassword(user.getPasswordHash());
                userRepository.save(user);
            }

            if (!user.isTotpConfirmed()) {
                if (!user.hasTotpSecret()) {
                    TotpEnrollmentService.EncryptedEnrollment enrollment = totpEnrollmentService.enroll(user.getUsername());
                    user.setTotpSecret(enrollment.encryptedSecret());
                    user.setMfaEnabled(true);
                    user.setTotpConfirmed(false);
                    user.setTotpLastStep(null);
                    userRepository.save(user);
                }
                auditLogService.log("TOTP_SETUP_REQUIRED", "User", String.valueOf(user.getId()),
                        "Google Authenticator setup required before a session is issued", httpRequest);
                Map<String, Object> setup = new HashMap<>();
                setup.put("status", "MFA_SETUP_REQUIRED");
                setup.put("mfaSetupToken", jwtService.generateMfaSetupToken(user.getUsername()));
                setup.put("message", "Set up Google Authenticator, then enter the 6-digit code.");
                return ResponseEntity.ok(setup);
            }

            auditLogService.log("OTP_CHALLENGE", "User", String.valueOf(user.getId()),
                    "Google Authenticator code required", httpRequest);
            Map<String, Object> challenge = new HashMap<>();
            challenge.put("status", "MFA_REQUIRED");
            challenge.put("mfaType", "TOTP");
            challenge.put("mfaToken", jwtService.generateMfaToken(user.getUsername()));
            challenge.put("message", "Enter the 6-digit code from Google Authenticator, or a one-time recovery code.");
            return ResponseEntity.ok(challenge);
        } catch (Exception e) {
            loginAttemptService.recordFailure(username, ip);
            auditLogService.log("LOGIN_FAILED", "User", username, "Login failed", httpRequest);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }
    }

    @PostMapping("/auth/mfa/verify")
    public ResponseEntity<?> verifyMfa(@RequestBody MfaVerifyRequest request, HttpServletRequest httpRequest) {
        String token = mfaToken(request, httpRequest);
        if (token == null || !jwtService.isScopedTokenValid(token, TokenScopes.MFA)) {
            auditLogService.log("OTP_FAILED", "User", "unknown", "MFA verification without a valid mfa token", httpRequest);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Sign in again. A verification token is required."));
        }

        String username = jwtService.extractUsername(token);
        String claimed = request.getUsername() == null ? "" : request.getUsername().trim();
        if (!claimed.isEmpty() && !claimed.equalsIgnoreCase(username)) {
            auditLogService.log("OTP_FAILED", "User", username, "MFA token did not match the submitted username", httpRequest);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired code"));
        }

        String ip = clientIpResolver.resolve(httpRequest);
        try {
            if (loginAttemptService.isBlocked(username, ip)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("error", "Too many failed attempts. Please try again later."));
            }

            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null || !user.isTotpConfirmed()) {
                auditLogService.log("OTP_FAILED", "User", username, "Invalid or expired MFA code", httpRequest);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid or expired code"));
            }

            boolean ok = acceptTotp(user, request.getCode());
            if (!ok) {
                ok = recoveryCodeService.consume(user, request.getCode());
            }
            if (!ok) {
                loginAttemptService.recordFailure(username, ip);
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

            loginAttemptService.recordSuccess(username, ip);
            auditLogService.log("OTP_VERIFIED", "User", String.valueOf(user.getId()),
                    "User logged in successfully with MFA", httpRequest);
            auditLogService.log("LOGIN_SUCCESS", "User", String.valueOf(user.getId()),
                    "User logged in successfully with MFA", httpRequest);
            return ResponseEntity.ok(sessionPayload(user));
        } catch (ResponseStatusException rse) {
            return ResponseEntity.status(rse.getStatusCode())
                    .body(Map.of("error", rse.getReason() != null ? rse.getReason() : "Request blocked"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Verification failed"));
        }
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout(HttpServletRequest httpRequest) {
        User user = currentAccessUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Sign in again."));
        }
        user.bumpTokenVersion();
        userRepository.save(user);
        auditLogService.log("LOGOUT", "User", String.valueOf(user.getId()), "Session ended", httpRequest);
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    @PostMapping("/auth/password-reset/request")
    public ResponseEntity<?> requestPasswordReset(@RequestBody Map<String, String> body, HttpServletRequest httpRequest) {
        String email = body.get("email") == null ? "" : body.get("email").trim();
        String clientKey = email + "|" + clientIpResolver.resolve(httpRequest);
        if (!rateLimitService.allowPasswordReset(clientKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many reset requests. Please try again later."));
        }
        userRepository.findByEmail(email).ifPresent(user -> {
            try {
                mfaService.sendMfaCode(user);
                auditLogService.log("PASSWORD_RESET_REQUESTED", "User", String.valueOf(user.getId()),
                        "Password reset code sent", httpRequest);
            } catch (Exception ignored) {
                // Do not reveal whether the email exists or delivery failed.
            }
        });
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "message", "If that email is registered, a reset code has been sent. Check Inbox and Spam."
        ));
    }

    @PostMapping("/auth/password-reset/confirm")
    public ResponseEntity<?> confirmPasswordReset(@RequestBody Map<String, String> body, HttpServletRequest httpRequest) {
        String email = body.get("email") == null ? "" : body.get("email").trim();
        String code = body.get("code") == null ? body.get("otp") : body.get("code");
        String newPassword = body.get("newPassword") == null ? body.get("password") : body.get("newPassword");

        if (email.isEmpty() || code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and code are required"));
        }
        if (newPassword == null || PasswordPolicy.isFactoryDefault(newPassword) || newPassword.length() < PasswordPolicy.MIN_LENGTH) {
            return ResponseEntity.badRequest().body(Map.of("error", PasswordPolicy.requirementMessage()));
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !mfaService.verifyCode(user, code)) {
            auditLogService.log("PASSWORD_RESET_FAILED", "User", email, "Invalid reset attempt", httpRequest);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired code"));
        }

        String encoded = passwordEncoder.encode(newPassword);
        user.setPassword(encoded);
        user.setPasswordHash(encoded);
        user.setMustChangePassword(false);
        user.bumpTokenVersion();
        userRepository.save(user);
        auditLogService.log("PASSWORD_RESET", "User", String.valueOf(user.getId()),
                "Password reset completed", httpRequest);
        return ResponseEntity.ok(Map.of("status", "OK", "message", "Password updated successfully"));
    }

    @PostMapping("/auth/password/replace-default")
    public ResponseEntity<?> replaceDefaultPassword(@RequestBody Map<String, String> body, HttpServletRequest httpRequest) {
        String username = body.get("username") == null ? "" : body.get("username").trim();
        String currentPassword = body.get("currentPassword");
        String newPassword = body.get("newPassword");
        if (username.isEmpty() || currentPassword == null || currentPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Current password is required"));
        }
        try {
            PasswordPolicy.require(newPassword);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
        if (!passwordAccepted(username, currentPassword)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Current password is incorrect"));
        }
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Current password is incorrect"));
        }
        String encoded = passwordEncoder.encode(newPassword);
        user.setPassword(encoded);
        user.setPasswordHash(encoded);
        user.setMustChangePassword(false);
        user.bumpTokenVersion();
        userRepository.save(user);
        auditLogService.log("PASSWORD_CHANGED", "User", String.valueOf(user.getId()),
                "Password replaced", httpRequest);
        return ResponseEntity.ok(Map.of("status", "OK", "message", "Password updated. Sign in with the new password."));
    }

    @GetMapping("/me/totp-qr")
    public ResponseEntity<?> currentTotpQr(HttpServletRequest httpRequest) {
        User user = currentUser();
        if (user == null || !user.hasTotpSecret()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Google Authenticator is not set up for this account."));
        }
        if (user.isTotpConfirmed()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Google Authenticator is already set up. Reset it with your password and a current code."));
        }
        auditLogService.log("TOTP_QR_VIEWED", "User", String.valueOf(user.getId()),
                "User opened Google Authenticator QR during enrollment", httpRequest);
        return ResponseEntity.ok(Map.of(
                "otpauthUri", totpEnrollmentService.otpauthUri(user.getUsername(), user.getTotpSecret()),
                "issuer", TotpService.ISSUER
        ));
    }

    @PostMapping("/me/totp/confirm")
    public ResponseEntity<?> confirmTotp(@RequestBody Map<String, String> body, HttpServletRequest httpRequest) {
        User user = currentUser();
        if (user == null || !user.hasTotpSecret()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Google Authenticator is not set up for this account."));
        }
        if (user.isTotpConfirmed()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Google Authenticator is already confirmed."));
        }
        String code = body == null ? "" : body.getOrDefault("code", "");
        if (!acceptTotp(user, code)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "That code did not match. Check Google Authenticator and try again."));
        }
        user.setTotpConfirmed(true);
        user.setTotpPrompted(true);
        userRepository.save(user);
        List<String> recoveryCodes = recoveryCodeService.replaceAll(user);
        auditLogService.log("TOTP_CONFIRMED", "User", String.valueOf(user.getId()),
                "Google Authenticator confirmed", httpRequest);
        Map<String, Object> payload = new HashMap<>(sessionPayload(user));
        payload.put("recoveryCodes", recoveryCodes);
        payload.put("status", "OK");
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/me/totp/reenroll")
    public ResponseEntity<?> reenrollTotp(@RequestBody Map<String, String> body, HttpServletRequest httpRequest) {
        User user = currentAccessUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Sign in again."));
        }
        String currentPassword = body == null ? null : body.get("currentPassword");
        String code = body == null ? "" : body.getOrDefault("code", "");
        if (currentPassword == null || currentPassword.isBlank() || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Current password and authenticator code are required."));
        }
        if (!passwordAccepted(user.getUsername(), currentPassword) || !user.isTotpConfirmed() || !acceptTotp(user, code)) {
            auditLogService.log("TOTP_REENROLL_FAILED", "User", String.valueOf(user.getId()),
                    "Authenticator reset rejected", httpRequest);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Password or authenticator code was not accepted."));
        }
        TotpEnrollmentService.EncryptedEnrollment enrollment = totpEnrollmentService.enroll(user.getUsername());
        user.setTotpSecret(enrollment.encryptedSecret());
        user.setTotpConfirmed(false);
        user.setTotpPrompted(false);
        user.setTotpLastStep(null);
        user.bumpTokenVersion();
        userRepository.save(user);
        recoveryCodeService.deleteAll(user.getId());
        auditLogService.log("TOTP_REENROLL", "User", String.valueOf(user.getId()),
                "Authenticator reset started", httpRequest);
        return ResponseEntity.ok(Map.of(
                "status", "MFA_SETUP_REQUIRED",
                "mfaSetupToken", jwtService.generateMfaSetupToken(user.getUsername()),
                "message", "Scan the new QR code, then enter the 6-digit code."
        ));
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<?> legacyLogin(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return login(request, httpRequest);
    }

    private boolean acceptTotp(User user, String code) {
        Long step = totpEnrollmentService.matchingStep(user.getTotpSecret(), code);
        if (step == null || TotpService.isReplay(user.getTotpLastStep(), step)) {
            return false;
        }
        user.setTotpLastStep(step);
        userRepository.save(user);
        return true;
    }

    private boolean passwordAccepted(String username, String password) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            return auth != null && auth.isAuthenticated();
        } catch (Exception e) {
            return false;
        }
    }

    private String mfaToken(MfaVerifyRequest request, HttpServletRequest httpRequest) {
        if (request != null && request.getMfaToken() != null && !request.getMfaToken().isBlank()) {
            return request.getMfaToken().trim();
        }
        String header = httpRequest.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    private User currentAccessUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if ("ROLE_MFA_SETUP".equals(authority.getAuthority())) {
                return null;
            }
        }
        return currentUser();
    }

    private Map<String, Object> sessionPayload(User user) {
        Map<String, Object> payload = new HashMap<>(toPayload(user));
        payload.put("token", jwtService.generateAccessToken(user.getUsername(), user.getTokenVersion()));
        return payload;
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

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", user.getId());
        payload.put("name", user.getFullName() != null ? user.getFullName() : user.getName());
        payload.put("recordName", user.getName() != null ? user.getName() : (user.getFullName() != null ? user.getFullName() : ""));
        payload.put("role", role);
        payload.put("username", user.getUsername());
        payload.put("email", user.getEmail() != null ? user.getEmail() : "");
        payload.put("mfaEnabled", user.isMfaEnabled());
        return payload;
    }
}
