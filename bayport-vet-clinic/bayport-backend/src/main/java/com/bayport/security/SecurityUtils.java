package com.bayport.security;

import com.bayport.entity.User;
import com.bayport.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Shared role checks for controllers. Maps legacy {@code users.role} and {@code user_roles} consistently.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static boolean isAdministrator(Authentication auth, UserRepository userRepository) {
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        String principal = auth.getName();
        if (principal == null || principal.isBlank() || "anonymousUser".equalsIgnoreCase(principal)) {
            return false;
        }

        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (isAdminAuthority(authority != null ? authority.getAuthority() : null)) {
                return true;
            }
        }

        if (userRepository == null) {
            return false;
        }
        return userRepository.findByUsername(principal).map(SecurityUtils::isAdminUser).orElse(false);
    }

    public static boolean isAdminUser(User user) {
        if (user == null) {
            return false;
        }
        if (user.getRole() != null) {
            String legacy = user.getRole().trim().toLowerCase();
            if ("admin".equals(legacy) || "administrator".equals(legacy)) {
                return true;
            }
        }
        if (user.getRoles() != null) {
            for (var role : user.getRoles()) {
                if (role != null && isAdminAuthority(role.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String normalizeRoleAuthority(String role) {
        if (role == null || role.isBlank()) {
            return "ROLE_USER";
        }
        String normalized = role.trim().toUpperCase().replace('-', '_');
        if (normalized.startsWith("ROLE_")) {
            return normalized;
        }
        return "ROLE_" + normalized;
    }

    private static boolean isAdminAuthority(String authority) {
        if (authority == null || authority.isBlank()) {
            return false;
        }
        String upper = authority.trim().toUpperCase();
        return "ROLE_ADMIN".equals(upper)
                || "ADMIN".equals(upper)
                || "ADMINISTRATOR".equals(upper);
    }
}
