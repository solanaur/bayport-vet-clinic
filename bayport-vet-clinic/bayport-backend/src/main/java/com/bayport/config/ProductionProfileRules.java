package com.bayport.config;

import java.util.Locale;

/**
 * Startup checks for the clinic production profile.
 * H2 stays on the desktop and h2 profiles. Production is MySQL because the Flyway scripts are MySQL SQL.
 */
public final class ProductionProfileRules {

    private ProductionProfileRules() {}

    public static void requireClinicDatabase(String jdbcUrl) {
        String url = jdbcUrl == null ? "" : jdbcUrl.trim().toLowerCase(Locale.ROOT);
        if (url.startsWith("jdbc:mysql:")) {
            return;
        }
        throw new IllegalStateException(
                "Clinic production must use MySQL (jdbc:mysql://127.0.0.1:3306/bayport_db). "
                        + "H2 is for development on one computer. "
                        + "The Flyway scripts are MySQL SQL, so do not point this profile at PostgreSQL.");
    }

    public static void requireCorsOrigins(String origins) {
        if (origins == null || origins.isBlank()) {
            throw new IllegalStateException(
                    "Set SPRING_WEB_CORS_ALLOWED_ORIGINS to the clinic address, for example https://CLINIC-PC:8443");
        }
        for (String part : origins.split(",")) {
            String origin = part.trim().toLowerCase(Locale.ROOT);
            if (origin.isEmpty()) {
                continue;
            }
            if ("*".equals(origin) || origin.contains("*") || origin.startsWith("file:") || "null".equals(origin)) {
                throw new IllegalStateException(
                        "Production CORS cannot use *, a wildcard, or file://. Use the clinic https address.");
            }
        }
    }

    public static void requireTransport(boolean sslEnabled, boolean allowLanHttp, String keyStore, String keyStorePassword) {
        if (sslEnabled) {
            if (keyStore == null || keyStore.isBlank() || keyStorePassword == null || keyStorePassword.isBlank()) {
                throw new IllegalStateException(
                        "HTTPS is on. Set SERVER_SSL_KEY_STORE and SERVER_SSL_KEY_STORE_PASSWORD "
                                + "(see deploy/clinic/new-local-cert.ps1).");
            }
            return;
        }
        if (!allowLanHttp) {
            throw new IllegalStateException(
                    "Clinic production requires HTTPS. Set BAYPORT_SSL_ENABLED=true and the keystore, "
                            + "or set BAYPORT_ALLOW_LAN_HTTP=true only when every staff computer is on the clinic's private network.");
        }
    }
}
