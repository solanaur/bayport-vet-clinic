package com.bayport.security;

import java.util.Locale;
import java.util.Set;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;

    private static final Set<String> FACTORY_PASSWORDS = Set.of(
            "admin123",
            "vet123",
            "frontdesk123"
    );

    private PasswordPolicy() {}

    public static void require(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (isFactoryDefault(password)) {
            throw new IllegalArgumentException("Choose a password that is not a built-in clinic default");
        }
    }

    public static boolean isFactoryDefault(String password) {
        if (password == null) {
            return false;
        }
        return FACTORY_PASSWORDS.contains(password.trim().toLowerCase(Locale.ROOT));
    }

    public static String requirementMessage() {
        return "Password must be at least " + MIN_LENGTH + " characters";
    }
}
