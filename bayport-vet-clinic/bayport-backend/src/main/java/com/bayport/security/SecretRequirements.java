package com.bayport.security;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Startup rules for secrets. Short values are rejected. They are never padded out to a longer key.
 */
public final class SecretRequirements {

    public static final int MIN_SECRET_BYTES = 32;
    public static final int MIN_DB_PASSWORD_LENGTH = 12;

    private static final Set<String> WEAK_DB_PASSWORDS = Set.of(
            "mypass",
            "password",
            "password123",
            "admin",
            "admin123",
            "vet123",
            "frontdesk123",
            "sa",
            "root",
            "changeme",
            "bayport",
            "secret"
    );

    private SecretRequirements() {}

    public static byte[] requireAtLeast32Bytes(String value, String envName) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(envName
                    + " is missing or shorter than 32 bytes. Set "
                    + envName
                    + " to a random value of at least 32 bytes before starting.");
        }
        return bytes;
    }

    public static void requireDatabasePassword(String password) {
        String value = password == null ? "" : password;
        if (value.isBlank()
                || value.length() < MIN_DB_PASSWORD_LENGTH
                || WEAK_DB_PASSWORDS.contains(value.trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "Database password is missing or too weak. Set BAYPORT_DB_PASSWORD (desktop H2) or "
                            + "SPRING_DATASOURCE_PASSWORD (MySQL/Postgres) to at least 12 characters "
                            + "that are not a known default.");
        }
    }
}
