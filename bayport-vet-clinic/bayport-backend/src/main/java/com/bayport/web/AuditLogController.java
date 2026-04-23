package com.bayport.web;

import com.bayport.entity.AuditLog;
import com.bayport.entity.OperationLog;
import com.bayport.entity.DataAccessLog;
import com.bayport.entity.User;
import com.bayport.repository.AuditLogRepository;
import com.bayport.repository.OperationLogRepository;
import com.bayport.repository.DataAccessLogRepository;
import com.bayport.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final OperationLogRepository operationLogRepository;
    private final DataAccessLogRepository dataAccessLogRepository;
    private final UserRepository userRepository;

    public AuditLogController(AuditLogRepository auditLogRepository,
                              OperationLogRepository operationLogRepository,
                              DataAccessLogRepository dataAccessLogRepository,
                              UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.operationLogRepository = operationLogRepository;
        this.dataAccessLogRepository = dataAccessLogRepository;
        this.userRepository = userRepository;
    }
    
    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
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
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return false;
        }
        User user = userOpt.get();
        
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
    public Page<AuditLog> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return auditLogRepository.findAll(pageable);
    }

    @GetMapping("/organized")
    public ResponseEntity<?> getOrganizedActivityLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access Denied", "message", "Only administrators can access activity logs"));
        }
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        
        // Get all logs
        Page<AuditLog> auditLogsPage = auditLogRepository.findAll(pageable);
        List<OperationLog> operationLogs = operationLogRepository.findAll(Sort.by("ts").descending());
        Page<DataAccessLog> dataAccessLogsPage = dataAccessLogRepository.findAll(pageable);
        
        // Organize audit logs by action type
        Map<String, List<AuditLog>> auditByType = auditLogsPage.getContent().stream()
                .collect(Collectors.groupingBy(log -> {
                    String action = log.getAction();
                    if (action.startsWith("CREATE") || action.contains("CREATED")) return "CREATE";
                    if (action.startsWith("UPDATE") || action.contains("UPDATED")) return "UPDATE";
                    if (action.startsWith("DELETE") || action.contains("DELETED")) return "DELETE";
                    if (action.contains("LOGIN") || action.contains("LOGOUT")) return "AUTHENTICATION";
                    if (action.contains("MFA") || action.contains("OTP")) return "SECURITY";
                    return "OTHER";
                }));
        
        // Organize operation logs by type
        Map<String, List<OperationLog>> operationByType = operationLogs.stream()
                .collect(Collectors.groupingBy(log -> {
                    String type = log.getType();
                    if (type.contains("PET")) return "PET_OPERATIONS";
                    if (type.contains("APPT") || type.contains("APPOINTMENT")) return "APPOINTMENT_OPERATIONS";
                    if (type.contains("RX") || type.contains("PRESCRIPTION")) return "PRESCRIPTION_OPERATIONS";
                    return "OTHER_OPERATIONS";
                }));
        
        // Organize data access logs by operation
        Map<String, List<DataAccessLog>> dataAccessByType = dataAccessLogsPage.getContent().stream()
                .collect(Collectors.groupingBy(DataAccessLog::getOperation));
        
        Map<String, Object> result = new HashMap<>();
        result.put("auditLogsByType", auditByType);
        result.put("operationLogsByType", operationByType);
        result.put("dataAccessLogsByType", dataAccessByType);
        result.put("totalAuditLogs", auditLogsPage.getTotalElements());
        result.put("totalOperationLogs", operationLogs.size());
        result.put("totalDataAccessLogs", dataAccessLogsPage.getTotalElements());
        
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAuditLog(@PathVariable Long id) {
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access Denied"));
        }
        if (auditLogRepository.existsById(id)) {
            auditLogRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Audit log deleted"));
        }
        return ResponseEntity.notFound().build();
    }
    
    @DeleteMapping("/operations/{id}")
    public ResponseEntity<?> deleteOperationLog(@PathVariable Long id) {
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access Denied"));
        }
        if (operationLogRepository.existsById(id)) {
            operationLogRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Operation log deleted"));
        }
        return ResponseEntity.notFound().build();
    }
    
    @DeleteMapping("/data-access/{id}")
    public ResponseEntity<?> deleteDataAccessLog(@PathVariable Long id) {
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access Denied"));
        }
        if (dataAccessLogRepository.existsById(id)) {
            dataAccessLogRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Data access log deleted"));
        }
        return ResponseEntity.notFound().build();
    }
}
