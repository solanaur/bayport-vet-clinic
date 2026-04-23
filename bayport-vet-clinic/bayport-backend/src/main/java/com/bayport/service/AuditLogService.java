package com.bayport.service;

import com.bayport.entity.AuditLog;
import com.bayport.entity.User;
import com.bayport.repository.AuditLogRepository;
import com.bayport.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditLogService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    public void log(String action, String entityName, String entityId, String details) {
        log(action, entityName, entityId, details, null);
    }

    public void log(String action, String entityName, String entityId, String details, HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setTimestamp(LocalDateTime.now());
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            log.setUsername(auth.getName());
            User user = userRepository.findByUsername(auth.getName()).orElse(null);
            if (user != null) {
                log.setUserId(user.getId());
            }
        }
        
        log.setAction(action);
        log.setEntityName(entityName);
        log.setEntityId(entityId);
        log.setDetails(details);
        
        if (request != null) {
            String ipAddress = getClientIpAddress(request);
            log.setIpAddress(ipAddress);
        }
        
        auditLogRepository.save(log);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
