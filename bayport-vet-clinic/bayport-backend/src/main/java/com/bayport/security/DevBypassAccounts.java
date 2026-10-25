package com.bayport.security;

import java.util.List;
import java.util.Locale;

/**
 * Local-only usernames that sign in with a password and skip Google Authenticator.
 * They are created only when {@code bayport.dev.bypass-logins} is true.
 */
public final class DevBypassAccounts {

    public record Account(String username, String password, String name, String role, String authority) {}

    public static final List<Account> ACCOUNTS = List.of(
            new Account("admin", "admin123", "Administrator", "admin", "ROLE_ADMIN"),
            new Account("vet", "vet123", "Veterinarian", "vet", "ROLE_VET"),
            new Account("front", "front123", "Front Office", "front_office", "ROLE_FRONT_OFFICE")
    );

    private DevBypassAccounts() {}

    public static boolean matches(String username, String password) {
        return find(username, password) != null;
    }

    public static Account find(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        String name = username.trim().toLowerCase(Locale.ROOT);
        for (Account account : ACCOUNTS) {
            if (account.username().equals(name) && account.password().equals(password)) {
                return account;
            }
        }
        return null;
    }

    public static Account findByUsername(String username) {
        if (username == null) {
            return null;
        }
        String name = username.trim().toLowerCase(Locale.ROOT);
        for (Account account : ACCOUNTS) {
            if (account.username().equals(name)) {
                return account;
            }
        }
        return null;
    }
}
