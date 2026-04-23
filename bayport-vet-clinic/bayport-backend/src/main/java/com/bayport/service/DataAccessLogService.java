package com.bayport.service;

import com.bayport.entity.DataAccessLog;
import com.bayport.entity.User;
import com.bayport.repository.DataAccessLogRepository;
import com.bayport.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DataAccessLogService {
    private final DataAccessLogRepository dataAccessLogRepository;
    private final UserRepository userRepository;

    public DataAccessLogService(DataAccessLogRepository dataAccessLogRepository, UserRepository userRepository) {
        this.dataAccessLogRepository = dataAccessLogRepository;
        this.userRepository = userRepository;
    }

    public void logRead(String entityName, String entityId, String details) {
        logRead(entityName, entityId, details, null);
    }

    public void logRead(String entityName, String entityId, String details, HttpServletRequest request) {
        DataAccessLog log = new DataAccessLog();
        log.setTimestamp(LocalDateTime.now());
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            log.setUsername(auth.getName());
            User user = userRepository.findByUsername(auth.getName()).orElse(null);
            if (user != null) {
                log.setUserId(user.getId());
            }
        }
        
        log.setEntityName(entityName);
        log.setEntityId(entityId);
        log.setOperation("READ");
        log.setDetails(details);
        
        if (request != null) {
            String ipAddress = getClientIpAddress(request);
            log.setIpAddress(ipAddress);
        }
        
        dataAccessLogRepository.save(log);
    }

    public void logOperation(String entityName, String entityId, String operation, String details) {
        logOperation(entityName, entityId, operation, details, null);
    }

    public void logOperation(String entityName, String entityId, String operation, String details, HttpServletRequest request) {
        DataAccessLog log = new DataAccessLog();
        log.setTimestamp(LocalDateTime.now());
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            log.setUsername(auth.getName());
            User user = userRepository.findByUsername(auth.getName()).orElse(null);
            if (user != null) {
                log.setUserId(user.getId());
            }
        }
        
        log.setEntityName(entityName);
        log.setEntityId(entityId);
        log.setOperation(operation);
        log.setDetails(details);
        
        if (request != null) {
            String ipAddress = getClientIpAddress(request);
            log.setIpAddress(ipAddress);
        }
        
        dataAccessLogRepository.save(log);
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
