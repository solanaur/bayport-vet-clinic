package com.bayport.web;

import com.bayport.entity.User;
import com.bayport.repository.UserRepository;
import com.bayport.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class TosController {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Value("${app.tos.version:v1.0}")
    private String currentTosVersion;

    public TosController(UserRepository userRepository, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/tos/check")
    public Map<String, Object> checkTos() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            return Map.of("requiresAcceptance", false);
        }

        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        if (user == null) {
            return Map.of("requiresAcceptance", false);
        }

        boolean requiresAcceptance = user.getTosVersionAccepted() == null || 
                                     !user.getTosVersionAccepted().equals(currentTosVersion);

        return Map.of(
            "requiresAcceptance", requiresAcceptance,
            "currentVersion", currentTosVersion,
            "acceptedVersion", user.getTosVersionAccepted() != null ? user.getTosVersionAccepted() : "none"
        );
    }

    @PostMapping("/tos-accept")
    public ResponseEntity<?> acceptTos(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setTosVersionAccepted(currentTosVersion);
        user.setTosAcceptedAt(LocalDateTime.now());
        userRepository.save(user);

        auditLogService.log("TOS_ACCEPTED", "User", String.valueOf(user.getId()), 
            "Accepted TOS version: " + currentTosVersion, request);

        return ResponseEntity.ok(Map.of("status", "OK", "version", currentTosVersion));
    }
}
