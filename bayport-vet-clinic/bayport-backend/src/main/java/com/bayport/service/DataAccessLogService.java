package com.bayport.service;

import com.bayport.entity.DataAccessLog;
import com.bayport.entity.User;
import com.bayport.repository.DataAccessLogRepository;
import com.bayport.repository.UserRepository;
import com.bayport.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DataAccessLogService {
    private final DataAccessLogRepository dataAccessLogRepository;
    private final UserRepository userRepository;
    private final ClientIpResolver clientIpResolver;

    public DataAccessLogService(DataAccessLogRepository dataAccessLogRepository,
                                UserRepository userRepository,
                                ClientIpResolver clientIpResolver) {
        this.dataAccessLogRepository = dataAccessLogRepository;
        this.userRepository = userRepository;
        this.clientIpResolver = clientIpResolver;
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
            log.setIpAddress(clientIpResolver.resolve(request));
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
            log.setIpAddress(clientIpResolver.resolve(request));
        }
        
        dataAccessLogRepository.save(log);
    }
}
