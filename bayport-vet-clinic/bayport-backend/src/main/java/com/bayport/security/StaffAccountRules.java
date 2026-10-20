package com.bayport.security;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Roles an administrator can create, and when that administrator must sign out.
 */
public final class StaffAccountRules {

    public static final Set<String> OPEN_ROLES = Set.of("admin", "vet", "front_office");

    private StaffAccountRules() {}

    public static String canonicalRole(String role) {
        if (role == null) {
            return "";
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "veterinarian" -> "vet";
            case "administrator" -> "admin";
            case "receptionist", "pharmacist", "staff" -> "front_office";
            default -> normalized;
        };
    }

    public static boolean allowed(String role) {
        return OPEN_ROLES.contains(canonicalRole(role));
    }

    /** Sign out only when the account that is signed in right now was removed. */
    public static boolean endCurrentSession(String currentUsername, Collection<String> removedUsernames) {
        if (currentUsername == null || currentUsername.isBlank() || removedUsernames == null) {
            return false;
        }
        String current = currentUsername.trim();
        for (String removed : removedUsernames) {
            if (removed != null && removed.equalsIgnoreCase(current)) {
                return true;
            }
        }
        return false;
    }
}
