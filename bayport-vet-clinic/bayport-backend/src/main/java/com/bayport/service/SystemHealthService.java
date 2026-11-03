package com.bayport.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Live operational checks for the System Health admin screen.
 * Never includes credentials, secrets, or full filesystem paths in the response.
 */
@Service
public class SystemHealthService {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a").withLocale(Locale.US);

    private final DataSource dataSource;
    private final EmailService emailService;
    private final ClinicBackupService clinicBackupService;
    private final String configuredVersion;
    private final String uploadDir;
    private final String backupDir;

    public SystemHealthService(DataSource dataSource,
                               EmailService emailService,
                               ClinicBackupService clinicBackupService,
                               @Value("${bayport.app.version:}") String configuredVersion,
                               @Value("${bayport.upload-dir:uploads}") String uploadDir,
                               @Value("${bayport.backup.dir:}") String backupDir) {
        this.dataSource = dataSource;
        this.emailService = emailService;
        this.clinicBackupService = clinicBackupService;
        this.configuredVersion = configuredVersion == null ? "" : configuredVersion.trim();
        this.uploadDir = uploadDir == null ? "uploads" : uploadDir.trim();
        this.backupDir = backupDir == null ? "" : backupDir.trim();
    }

    public Map<String, Object> snapshot() {
        Instant checkedAt = Instant.now();
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> storage = storageStatus();
        Map<String, Object> email = emailService.connectivityStatus();
        body.put("version", resolveVersion());
        body.put("database", databaseStatus());
        body.put("email", email);
        body.put("storage", storage);
        body.put("backup", clinicBackupService.healthStatus());
        body.put("checkedAt", checkedAt.toString());
        body.put("checkedAtDisplay", DISPLAY_TIME.format(checkedAt.atZone(ZoneId.systemDefault())));
        // Back-compat keys used by older clients / dashboard snippets
        body.put("mailConfigured", Boolean.TRUE.equals(email.get("configured")));
        body.put("diskFreeBytes", storage.get("freeBytes"));
        return body;
    }

    private String resolveVersion() {
        if (!configuredVersion.isBlank() && !configuredVersion.contains("@project")) {
            return configuredVersion;
        }
        Package pkg = getClass().getPackage();
        if (pkg != null) {
            String impl = pkg.getImplementationVersion();
            if (impl != null && !impl.isBlank()) {
                return impl.trim();
            }
        }
        try {
            String fromPom = SystemHealthService.class.getPackage().getSpecificationVersion();
            if (fromPom != null && !fromPom.isBlank()) {
                return fromPom.trim();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "unknown";
    }

    private Map<String, Object> databaseStatus() {
        Map<String, Object> db = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            boolean ok = connection.isValid(3);
            db.put("connected", ok);
            db.put("status", ok ? "Connected" : "Disconnected");
        } catch (Exception ex) {
            db.put("connected", false);
            db.put("status", "Disconnected");
        }
        return db;
    }

    private Map<String, Object> storageStatus() {
        Map<String, Object> storage = new LinkedHashMap<>();
        try {
            Path probe = resolveStorageProbePath();
            Files.createDirectories(probe);
            FileStore store = Files.getFileStore(probe);
            long free = store.getUsableSpace();
            long total = store.getTotalSpace();
            storage.put("available", true);
            storage.put("freeBytes", free);
            storage.put("totalBytes", total);
            storage.put("freeDisplay", formatBytes(free));
            storage.put("totalDisplay", formatBytes(total));
            storage.put("status", formatBytes(free) + " free");
        } catch (Exception ex) {
            storage.put("available", false);
            storage.put("freeBytes", -1L);
            storage.put("totalBytes", -1L);
            storage.put("freeDisplay", null);
            storage.put("totalDisplay", null);
            storage.put("status", "Unavailable");
        }
        return storage;
    }

    private Path resolveStorageProbePath() {
        Path upload = Path.of(uploadDir).toAbsolutePath().normalize();
        if (Files.exists(upload) || upload.getParent() == null || Files.isDirectory(upload.getParent())) {
            return upload;
        }
        Path backups = BackupRules.resolveDirectory(backupDir);
        if (Files.isDirectory(backups) || Files.isDirectory(backups.getParent())) {
            return backups;
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "Unavailable";
        }
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int idx = 0;
        while (value >= 1024 && idx < units.length - 1) {
            value /= 1024;
            idx++;
        }
        if (idx == 0) {
            return ((long) value) + " " + units[idx];
        }
        return String.format(Locale.US, "%.1f %s", value, units[idx]);
    }
}
