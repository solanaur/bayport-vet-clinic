package com.bayport.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads local SMTP credentials from {@code data/mail.env} when env vars are unset.
 * Electron already injects this file; Maven / IDE runs often do not.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MailEnvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE = "bayportMailEnvFile";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (hasMailCredentials(environment)) {
            return;
        }

        Path mailEnv = locateMailEnv();
        if (mailEnv == null) {
            return;
        }

        Map<String, Object> values = parseMailEnv(mailEnv);
        if (values.isEmpty()) {
            return;
        }

        // Prefer file only for keys that are still blank in the environment.
        Map<String, Object> toApply = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            String key = e.getKey();
            String existing = environment.getProperty(key);
            if (existing == null || existing.isBlank()) {
                toApply.put(key, e.getValue());
            }
        }
        if (toApply.isEmpty()) {
            return;
        }

        // Mirror common Spring aliases so ${spring.mail.username:${SPRING_MAIL_USERNAME:}} resolves.
        copyIfPresent(toApply, "SPRING_MAIL_USERNAME", "spring.mail.username");
        copyIfPresent(toApply, "SPRING_MAIL_PASSWORD", "spring.mail.password");
        copyIfPresent(toApply, "SPRING_MAIL_HOST", "spring.mail.host");
        copyIfPresent(toApply, "SPRING_MAIL_PORT", "spring.mail.port");

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, toApply));
        System.out.println("[bayport] Loaded SMTP settings from " + mailEnv.toAbsolutePath().normalize());
    }

    private static boolean hasMailCredentials(ConfigurableEnvironment environment) {
        String user = firstNonBlank(
                environment.getProperty("SPRING_MAIL_USERNAME"),
                environment.getProperty("spring.mail.username"));
        String pass = firstNonBlank(
                environment.getProperty("SPRING_MAIL_PASSWORD"),
                environment.getProperty("spring.mail.password"));
        return user != null && pass != null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return null;
    }

    private static Path locateMailEnv() {
        String override = System.getenv("BAYPORT_MAIL_ENV");
        if (override != null && !override.isBlank()) {
            Path p = Path.of(override.trim());
            if (Files.isRegularFile(p)) {
                return p;
            }
        }

        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path[] candidates = new Path[] {
                cwd.resolve("data/mail.env"),
                cwd.resolve("../data/mail.env"),
                cwd.resolve("bayport-vet-clinic/data/mail.env"),
                cwd.getParent() != null ? cwd.getParent().resolve("data/mail.env") : null
        };
        for (Path candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            Path normalized = candidate.normalize();
            if (Files.isRegularFile(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private static Map<String, Object> parseMailEnv(Path file) {
        Map<String, Object> map = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int idx = trimmed.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, idx).trim();
                String value = trimmed.substring(idx + 1).trim();
                if (key.isEmpty() || value.isEmpty()) {
                    continue;
                }
                if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                map.put(key, value);
            }
        } catch (IOException ignored) {
            // Leave mail unconfigured; EmailService will log the usual warning.
        }
        return map;
    }

    private static void copyIfPresent(Map<String, Object> map, String fromKey, String toKey) {
        Object value = map.get(fromKey);
        if (value != null && !map.containsKey(toKey)) {
            map.put(toKey, value);
        }
    }
}
