package com.bayport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootApplication
@EnableScheduling
public class BayportApplication {

    public static void main(String[] args) {
        loadLocalMailEnvIfNeeded();
        SpringApplication.run(BayportApplication.class, args);
    }

    /**
     * Electron injects {@code data/mail.env} into the process environment. Maven / IDE runs
     * usually do not — load the same file into system properties so OTP SMTP works locally.
     */
    private static void loadLocalMailEnvIfNeeded() {
        if (nonBlank(System.getenv("SPRING_MAIL_USERNAME")) && nonBlank(System.getenv("SPRING_MAIL_PASSWORD"))) {
            return;
        }
        if (nonBlank(System.getProperty("SPRING_MAIL_USERNAME")) && nonBlank(System.getProperty("SPRING_MAIL_PASSWORD"))) {
            return;
        }

        Path mailEnv = locateMailEnv();
        if (mailEnv == null) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(mailEnv, StandardCharsets.UTF_8);
            int applied = 0;
            for (String line : lines) {
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
                // Do not override real environment variables.
                if (nonBlank(System.getenv(key))) {
                    continue;
                }
                if (!nonBlank(System.getProperty(key))) {
                    System.setProperty(key, value);
                    applied++;
                }
                // Mirror into spring.mail.* for direct binding.
                if ("SPRING_MAIL_USERNAME".equals(key) && !nonBlank(System.getProperty("spring.mail.username"))) {
                    System.setProperty("spring.mail.username", value);
                } else if ("SPRING_MAIL_PASSWORD".equals(key) && !nonBlank(System.getProperty("spring.mail.password"))) {
                    System.setProperty("spring.mail.password", value);
                } else if ("SPRING_MAIL_HOST".equals(key) && !nonBlank(System.getProperty("spring.mail.host"))) {
                    System.setProperty("spring.mail.host", value);
                } else if ("SPRING_MAIL_PORT".equals(key) && !nonBlank(System.getProperty("spring.mail.port"))) {
                    System.setProperty("spring.mail.port", value);
                }
            }
            if (applied > 0) {
                System.out.println("[bayport] Loaded SMTP settings from " + mailEnv.toAbsolutePath().normalize());
            }
        } catch (IOException ignored) {
            // EmailService will warn if SMTP remains unconfigured.
        }
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
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
        Path[] candidates = {
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
}
