package com.bayport.security;

import java.util.Locale;

/**
 * The username {@code admin} with a factory password is a one-time setup login.
 * It is allowed only until an administrator with a non-factory password exists.
 */
public final class TemporaryAdminRules {

    public static final String USERNAME = "admin";

    private TemporaryAdminRules() {}

    public static boolean isTemporaryLogin(String username, String password) {
        return username != null
                && USERNAME.equalsIgnoreCase(username.trim())
                && PasswordPolicy.isFactoryDefault(password);
    }

    public static boolean isDurableAdmin(String role, boolean factoryPassword) {
        if (factoryPassword || role == null || role.isBlank()) {
            return false;
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        return "admin".equals(normalized) || "administrator".equals(normalized);
    }
}
