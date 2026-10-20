package com.bayport.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Client address for audit logs, login lockouts, and rate limits.
 * Uses the address Spring already resolved. With server.forward-headers-strategy=native,
 * that address includes a trusted proxy's forwarded client. Do not read X-Forwarded-For here.
 */
@Component
public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String addr = request.getRemoteAddr();
        if (addr == null || addr.isBlank()) {
            return "unknown";
        }
        return addr.trim();
    }
}
