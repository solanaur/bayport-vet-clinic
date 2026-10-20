package com.bayport.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Daily copy of the desktop H2 file. MySQL stays the path when more than one computer shares the records.
 */
@Component
public class DesktopDatabaseBackup {

    private static final Logger log = LoggerFactory.getLogger(DesktopDatabaseBackup.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int KEEP_DAYS = 14;

    private final String datasourceUrl;
    private final DataSource dataSource;

    public DesktopDatabaseBackup(@Value("${spring.datasource.url:}") String datasourceUrl,
                                 DataSource dataSource) {
        this.datasourceUrl = datasourceUrl == null ? "" : datasourceUrl;
        this.dataSource = dataSource;
    }

    /**
     * ClinicBackupService writes the backup outside the application folder.
     * This class no longer runs on its own schedule.
     */
    void backupOnStartup() {
    }

    void backupOvernight() {
    }

    void backup(String reason) {
        if (!datasourceUrl.startsWith("jdbc:h2:file:")) {
            return;
        }
        String filePart = datasourceUrl.substring("jdbc:h2:file:".length());
        int options = filePart.indexOf(';');
        if (options >= 0) {
            filePart = filePart.substring(0, options);
        }
        Path dbBase = Path.of(filePart);
        Path backupDir = dbBase.getParent() == null
                ? Path.of("data", "backups", LocalDate.now().format(DAY))
                : dbBase.getParent().resolve("backups").resolve(LocalDate.now().format(DAY));
        Path target = backupDir.resolve("bayport-db.zip");
        String sqlPath = target.toAbsolutePath().toString().replace('\\', '/').replace("'", "''");
        try {
            Files.createDirectories(backupDir);
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("BACKUP TO '" + sqlPath + "'");
            }
            pruneOldBackups(backupDir.getParent());
            log.info("Database backup saved ({}) at {}", reason, target);
        } catch (Exception e) {
            log.warn("Database backup failed ({}): {}", reason, e.getMessage());
        }
    }

    private void pruneOldBackups(Path backupsRoot) throws IOException {
        if (backupsRoot == null || !Files.isDirectory(backupsRoot)) {
            return;
        }
        LocalDate cutoff = LocalDate.now().minusDays(KEEP_DAYS);
        try (Stream<Path> days = Files.list(backupsRoot)) {
            for (Path day : days.filter(Files::isDirectory).toList()) {
                LocalDate parsed;
                try {
                    parsed = LocalDate.parse(day.getFileName().toString(), DAY);
                } catch (Exception ignored) {
                    continue;
                }
                if (parsed.isBefore(cutoff)) {
                    deleteTree(day);
                }
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
