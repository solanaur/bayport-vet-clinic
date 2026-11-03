package com.bayport.web;

import com.bayport.entity.AuditLog;
import com.bayport.entity.DataAccessLog;
import com.bayport.entity.OperationLog;
import com.bayport.entity.User;
import com.bayport.repository.AuditLogRepository;
import com.bayport.repository.DataAccessLogRepository;
import com.bayport.repository.OperationLogRepository;
import com.bayport.repository.UserRepository;
import com.bayport.service.ActivityLogRules;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

        boolean hasAdminAuthority = auth.getAuthorities().stream()
                .anyMatch(a -> {
                    String authority = a.getAuthority();
                    return "ROLE_ADMIN".equalsIgnoreCase(authority)
                            || "ADMIN".equalsIgnoreCase(authority)
                            || "ADMINISTRATOR".equalsIgnoreCase(authority);
                });
        if (hasAdminAuthority) {
            return true;
        }

        String username = auth.getName();
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return false;
        }
        User user = userOpt.get();

        if (user.getRole() != null) {
            String role = user.getRole().toLowerCase();
            if ("admin".equals(role) || "administrator".equals(role)) {
                return true;
            }
        }

        return user.getRoles().stream().anyMatch(r -> {
            String roleName = r.getName().toUpperCase();
            return "ROLE_ADMIN".equals(roleName)
                    || "ADMIN".equals(roleName)
                    || "ADMINISTRATOR".equals(roleName);
        });
    }

    @GetMapping
    public ResponseEntity<?> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access Denied"));
        }
        var pageable = org.springframework.data.domain.PageRequest.of(
                page, size, Sort.by("timestamp").descending());
        return ResponseEntity.ok(auditLogRepository.findAll(pageable));
    }

    /**
     * Unified activity feed grouped by module category (display-only classification).
     * Historical rows are not rewritten.
     */
    @GetMapping("/organized")
    public ResponseEntity<?> getOrganizedActivityLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "500") int size) {
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access Denied", "message", "Only administrators can access activity logs"));
        }

        int limit = Math.min(Math.max(size, 50), 2000);
        List<Map<String, Object>> activities = new ArrayList<>();

        for (AuditLog log : auditLogRepository.findAll(Sort.by("timestamp").descending())) {
            activities.add(toActivity(
                    "audit",
                    log.getId(),
                    log.getTimestamp(),
                    log.getUsername(),
                    ActivityLogRules.categorizeAudit(log.getAction(), log.getEntityName()),
                    ActivityLogRules.displayMessage(log.getAction(), log.getDetails(), log.getAction()),
                    log.getAction(),
                    log.getEntityName(),
                    log.getEntityId()
            ));
        }

        for (OperationLog log : operationLogRepository.findAll(Sort.by("ts").descending())) {
            activities.add(toActivity(
                    "operation",
                    log.getId(),
                    log.getTs(),
                    log.getUsername(),
                    ActivityLogRules.categorizeOperation(log.getType()),
                    ActivityLogRules.displayMessage(log.getType(), log.getMessage(), log.getType()),
                    log.getType(),
                    log.getPetId() != null ? "Pet" : null,
                    log.getPetId() != null ? String.valueOf(log.getPetId()) : null
            ));
        }

        for (DataAccessLog log : dataAccessLogRepository.findAll(Sort.by("timestamp").descending())) {
            String message = ActivityLogRules.displayMessage(
                    log.getOperation(),
                    log.getDetails(),
                    (log.getOperation() != null ? log.getOperation() : "Access")
                            + (log.getEntityName() != null ? " " + log.getEntityName() : "")
            );
            activities.add(toActivity(
                    "dataAccess",
                    log.getId(),
                    log.getTimestamp(),
                    log.getUsername(),
                    ActivityLogRules.categorizeDataAccess(log.getEntityName(), log.getOperation()),
                    message,
                    log.getOperation(),
                    log.getEntityName(),
                    log.getEntityId()
            ));
        }

        activities.sort(Comparator
                .comparing((Map<String, Object> row) -> (LocalDateTime) row.get("timestamp"),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(row -> ((Number) row.getOrDefault("id", 0L)).longValue(), Comparator.reverseOrder()));

        Map<String, Long> counts = new LinkedHashMap<>();
        for (String key : ActivityLogRules.CATEGORY_ORDER) {
            if (ActivityLogRules.ALL.equals(key)) {
                continue;
            }
            counts.put(key, 0L);
        }
        for (Map<String, Object> row : activities) {
            String cat = String.valueOf(row.get("category"));
            counts.put(cat, counts.getOrDefault(cat, 0L) + 1);
        }

        List<Map<String, Object>> categories = new ArrayList<>();
        Map<String, String> labels = ActivityLogRules.categoryLabels();
        categories.add(Map.of(
                "key", ActivityLogRules.ALL,
                "label", labels.get(ActivityLogRules.ALL),
                "count", activities.size()
        ));
        for (String key : ActivityLogRules.CATEGORY_ORDER) {
            if (ActivityLogRules.ALL.equals(key)) {
                continue;
            }
            long count = counts.getOrDefault(key, 0L);
            if (count <= 0) {
                continue; // only show categories that have records
            }
            Map<String, Object> tab = new LinkedHashMap<>();
            tab.put("key", key);
            tab.put("label", labels.getOrDefault(key, key));
            tab.put("count", count);
            categories.add(tab);
        }

        int from = Math.min(page * limit, activities.size());
        int to = Math.min(from + limit, activities.size());
        List<Map<String, Object>> pageItems = activities.subList(from, to);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categories", categories);
        result.put("activities", pageItems);
        result.put("total", activities.size());
        result.put("page", page);
        result.put("size", limit);
        result.put("hasMore", to < activities.size());
        // Backward-compatible empty maps so older clients do not crash
        result.put("auditLogsByType", Map.of());
        result.put("operationLogsByType", Map.of());
        result.put("dataAccessLogsByType", Map.of());
        return ResponseEntity.ok(result);
    }

    private static Map<String, Object> toActivity(String source,
                                                  Long id,
                                                  LocalDateTime timestamp,
                                                  String username,
                                                  String category,
                                                  String message,
                                                  String action,
                                                  String entityName,
                                                  String entityId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("source", source);
        row.put("timestamp", timestamp);
        row.put("username", username != null && !username.isBlank() ? username : "System");
        row.put("category", category);
        row.put("categoryLabel", ActivityLogRules.labelFor(category));
        row.put("message", message);
        row.put("action", action);
        row.put("entityName", entityName);
        row.put("entityId", entityId);
        return row;
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
