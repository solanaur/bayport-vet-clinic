package com.bayport.service;

import com.bayport.entity.BackupRun;
import com.bayport.entity.User;
import com.bayport.repository.BackupRunRepository;
import com.bayport.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ClinicBackupService {

    private static final Logger log = LoggerFactory.getLogger(ClinicBackupService.class);
    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final DataSource dataSource;
    private final BackupRunRepository backupRunRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final String backupDir;
    private final String externalDir;
    private final int keep;
    private final int keepDaily;
    private final int keepWeekly;
    private final String jdbcUrl;
    private final String dbUser;
    private final String dbPassword;
    private final String mysqldump;
    private final String mysqlProgram;
    private final String uploadDir;
    private final String verifyDatabase;
    private Instant lastStaleAlert = Instant.EPOCH;
    private final Object backupLock = new Object();
    private volatile boolean backupRunning;

    public ClinicBackupService(DataSource dataSource,
                               BackupRunRepository backupRunRepository,
                               UserRepository userRepository,
                               EmailService emailService,
                               @Value("${bayport.backup.dir:}") String backupDir,
                               @Value("${bayport.backup.external-dir:}") String externalDir,
                               @Value("${bayport.backup.keep:14}") int keep,
                               @Value("${bayport.backup.keep-daily:7}") int keepDaily,
                               @Value("${bayport.backup.keep-weekly:4}") int keepWeekly,
                               @Value("${spring.datasource.url:}") String jdbcUrl,
                               @Value("${spring.datasource.username:}") String dbUser,
                               @Value("${spring.datasource.password:}") String dbPassword,
                               @Value("${bayport.backup.mysqldump:}") String mysqldump,
                               @Value("${bayport.backup.mysql:}") String mysqlProgram,
                               @Value("${bayport.upload-dir:uploads}") String uploadDir,
                               @Value("${bayport.backup.verify-database:}") String verifyDatabase) {
        this.dataSource = dataSource;
        this.backupRunRepository = backupRunRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.backupDir = backupDir;
        this.externalDir = externalDir;
        this.keep = keep;
        this.keepDaily = keepDaily;
        this.keepWeekly = keepWeekly;
        this.jdbcUrl = jdbcUrl == null ? "" : jdbcUrl;
        this.dbUser = dbUser == null ? "" : dbUser;
        this.dbPassword = dbPassword == null ? "" : dbPassword;
        this.mysqldump = mysqldump == null ? "" : mysqldump;
        this.mysqlProgram = mysqlProgram == null ? "" : mysqlProgram;
        this.uploadDir = uploadDir == null ? "uploads" : uploadDir;
        this.verifyDatabase = verifyDatabase == null ? "" : verifyDatabase.trim();
    }

    @Scheduled(cron = "${bayport.backup.cron:0 15 2 * * *}")
    public void scheduledBackup() {
        try {
            createBackup("scheduled");
        } catch (IllegalStateException ex) {
            log.warn("Scheduled backup skipped: {}", ex.getMessage());
        }
    }

    @Scheduled(cron = "0 5 * * * *")
    public void scheduledStaleCheck() {
        Map<String, Object> status = status();
        if (Boolean.TRUE.equals(status.get("ok"))) {
            return;
        }
        Instant now = Instant.now();
        if (lastStaleAlert.plus(BackupRules.STALE_AFTER).isAfter(now)) {
            return;
        }
        lastStaleAlert = now;
        notifyAdmins("Bayport backup needs attention", String.valueOf(status.get("message")));
    }

    public boolean isBackupRunning() {
        return backupRunning;
    }

    public BackupRun createBackup(String reason) {
        beginBackupWork();
        try {
            return createBackupUnlocked(reason);
        } finally {
            endBackupWork();
        }
    }

    private BackupRun createBackupUnlocked(String reason) {
        BackupRun run = new BackupRun();
        run.setStartedAt(LocalDateTime.now());
        run.setStatus("RUNNING");
        run.setMessage(clip(reason));
        backupRunRepository.save(run);
        Path temp = null;
        Path defaults = null;
        try {
            Path dir = BackupRules.resolveDirectory(backupDir);
            Files.createDirectories(dir);
            String id = "bayport-" + LocalDateTime.now().format(ID_TIME);
            temp = Files.createTempDirectory("bayport-backup-");
            String entry = dumpDatabase(temp);
            String hash;
            try (InputStream input = Files.newInputStream(temp.resolve(entry))) {
                hash = BackupRules.sha256(input);
            }
            copyUploads(temp.resolve("uploads"));
            Files.writeString(temp.resolve("manifest.txt"),
                    "databaseEntry=" + entry + "\ndatabaseSha256=" + hash + "\n");
            Path zip = dir.resolve(id + ".zip");
            zipDirectory(temp, zip);
            if (!Files.isRegularFile(zip) || !Files.isReadable(zip)) {
                throw new IOException("Backup file was not created or is not readable.");
            }
            BackupRules.verifyArchive(zip);
            String verify = testRestore(temp.resolve(entry), entry);
            run.setStatus("SUCCESS");
            run.setFinishedAt(LocalDateTime.now());
            run.setLocation(id);
            run.setSizeBytes(Files.size(zip));
            try (InputStream input = Files.newInputStream(zip)) {
                run.setSha256(BackupRules.sha256(input));
            }
            run.setVerifyStatus(verify);
            run.setMessage(clip(reason));
            copyToExternal(zip);
            prune(dir);
            if (externalDir != null && !externalDir.isBlank()) {
                prune(Path.of(externalDir.trim()));
            }
            if (!"PASSED".equals(verify)) {
                notifyAdmins("Bayport backup needs a restore test",
                        "Backup " + id + " was saved. The restore test is " + verify + ".");
            }
            log.info("Clinic backup saved ({}) {}", reason, id);
        } catch (Exception ex) {
            run.setStatus("FAILED");
            run.setFinishedAt(LocalDateTime.now());
            run.setVerifyStatus("FAILED");
            run.setMessage(clip(ex.getMessage()));
            log.warn("Clinic backup failed ({}): {}", reason, ex.getMessage());
            notifyAdmins("Bayport backup failed", "The clinic backup did not finish. " + clip(ex.getMessage()));
        } finally {
            deleteTree(temp);
            deleteFile(defaults);
            backupRunRepository.save(run);
        }
        return run;
    }

    public List<Map<String, Object>> listBackups() throws IOException {
        Path dir = BackupRules.resolveDirectory(backupDir);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }

        Map<String, BackupRun> runsByLocation = new HashMap<>();
        for (BackupRun run : backupRunRepository.findAll()) {
            if (run.getLocation() == null || run.getLocation().isBlank()) {
                continue;
            }
            BackupRun existing = runsByLocation.get(run.getLocation());
            if (existing == null
                    || (run.getFinishedAt() != null
                    && (existing.getFinishedAt() == null || run.getFinishedAt().isAfter(existing.getFinishedAt())))) {
                runsByLocation.put(run.getLocation(), run);
            }
        }

        try (Stream<Path> files = Files.list(dir)) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".zip")).toList()) {
                String name = file.getFileName().toString();
                String id = name.substring(0, name.length() - 4);
                if (!BackupRules.BACKUP_ID.matcher(id).matches()) {
                    continue;
                }
                BackupRun run = runsByLocation.get(id);
                LocalDateTime when = run != null && run.getFinishedAt() != null
                        ? run.getFinishedAt()
                        : BackupRules.parseIdTimestamp(id);
                long size = Files.size(file);
                String status = "SUCCESS";
                String reason = "scheduled";
                String verifyStatus = null;
                if (run != null) {
                    status = run.getStatus() == null ? "SUCCESS" : run.getStatus();
                    reason = run.getMessage() == null ? "" : run.getMessage();
                    verifyStatus = run.getVerifyStatus();
                    if (run.getSizeBytes() != null && run.getSizeBytes() > 0) {
                        size = run.getSizeBytes();
                    }
                }
                boolean readable = Files.isRegularFile(file) && Files.isReadable(file);
                if (!readable) {
                    status = "UNREADABLE";
                }

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("filename", name);
                row.put("sizeBytes", size);
                row.put("status", status);
                row.put("reason", reason);
                row.put("manual", BackupRules.isPreservedReason(reason));
                row.put("verifyStatus", verifyStatus);
                row.put("finishedAt", when == null ? null : when.toString());
                row.put("finishedAtDisplay", formatBackupTime(when));
                row.put("confirm", BackupRules.confirmPhrase(id));
                rows.add(row);
            }
            rows.sort(Comparator.comparing((Map<String, Object> row) -> String.valueOf(row.get("id"))).reversed());
            return rows;
        }
    }

    public Path zipFor(String backupId) {
        BackupRules.requireBackupId(backupId);
        Path zip = BackupRules.resolveDirectory(backupDir).resolve(backupId + ".zip");
        if (!Files.isRegularFile(zip)) {
            throw new IllegalArgumentException("Unknown backup.");
        }
        return zip;
    }

    public Map<String, Object> restore(String backupId, String confirm) throws Exception {
        BackupRules.requireConfirm(backupId, confirm);
        Path zip = zipFor(backupId);

        beginBackupWork();
        try {
            BackupRules.verifyArchive(zip);

            BackupRun safety = createBackupUnlocked("safety-before-restore");
            if (!"SUCCESS".equals(safety.getStatus()) || safety.getLocation() == null) {
                throw new IllegalStateException(
                        "Could not create a safety backup of the current database. Restore was cancelled.");
            }

            BackupRules.verifyArchive(zip);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("backupId", backupId);
            result.put("safetyBackupId", safety.getLocation());

            if (jdbcUrl.startsWith("jdbc:mysql:")) {
                Path sql = extractDatabaseSql(zip);
                Path defaults = null;
                try {
                    BackupRules.MysqlTarget target = BackupRules.parseMysqlUrl(jdbcUrl);
                    defaults = writeDefaults(target);
                    runMysql(mysqlBinary(), defaults, target.database(), sql);
                } finally {
                    deleteFile(sql);
                    deleteFile(defaults);
                }
                verifyLiveConnectivity();
                log.info("Clinic database restored from {}", backupId);
                result.put("ok", true);
                result.put("connectivityVerified", true);
                result.put("message",
                        "Restore finished from " + backupId
                                + ". A safety backup was saved as " + safety.getLocation()
                                + ". Database connectivity was verified. Sign in again and check a few records.");
                return result;
            }

            if (jdbcUrl.startsWith("jdbc:h2:")) {
                result.put("connectivityVerified", false);
                result.put("message",
                        "Safety backup " + safety.getLocation()
                                + " was created and backup " + backupId
                                + " passed integrity checks. Stop Bayport, then replace the desktop database file from "
                                + backupId + ".zip before starting again. Current records were not overwritten.");
                throw new IllegalStateException(String.valueOf(result.get("message")));
            }

            throw new IllegalStateException("This database type cannot be restored automatically.");
        } finally {
            endBackupWork();
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> detail = healthStatus();
        boolean ok = Boolean.TRUE.equals(detail.get("ok"));
        String message = ok
                ? "Backups are up to date."
                : String.valueOf(detail.getOrDefault("message", "No successful backup found."));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", ok);
        body.put("message", message);
        body.put("finishedAt", detail.get("finishedAt"));
        body.put("finishedAtDisplay", detail.get("finishedAtDisplay"));
        return body;
    }

    /**
     * Health-screen detail: requires a SUCCESS run whose zip still exists and passes archive checks.
     */
    public Map<String, Object> healthStatus() {
        Map<String, Object> body = new LinkedHashMap<>();
        BackupRun latest = backupRunRepository.findFirstByStatusOrderByFinishedAtDesc("SUCCESS");
        if (latest == null || latest.getLocation() == null || latest.getLocation().isBlank()) {
            body.put("ok", false);
            body.put("found", false);
            body.put("fileValid", false);
            body.put("finishedAt", null);
            body.put("finishedAtDisplay", null);
            body.put("message", "No successful backup found.");
            return body;
        }

        Instant finished = latest.getFinishedAt() == null
                ? null
                : latest.getFinishedAt().atZone(ZoneId.systemDefault()).toInstant();
        boolean fresh = !BackupRules.stale(finished, Instant.now());
        boolean restorePassed = "PASSED".equals(latest.getVerifyStatus());

        Path zip;
        try {
            zip = zipFor(latest.getLocation());
        } catch (Exception ex) {
            body.put("ok", false);
            body.put("found", false);
            body.put("fileValid", false);
            body.put("finishedAt", finished == null ? null : finished.toString());
            body.put("finishedAtDisplay", formatBackupTime(latest.getFinishedAt()));
            body.put("message", "No successful backup found.");
            return body;
        }

        boolean fileValid = false;
        try {
            BackupRules.verifyArchive(zip);
            fileValid = true;
        } catch (Exception ex) {
            log.warn("Latest backup archive failed validation: {}", ex.getMessage());
        }

        boolean ok = fileValid && fresh && restorePassed;
        String message;
        if (!fileValid) {
            message = "No successful backup found.";
            ok = false;
        } else if (!fresh) {
            message = "The last successful backup is older than 24 hours.";
        } else if (!restorePassed) {
            message = "The last backup file is present, but the restore test has not passed.";
        } else {
            message = "Backups are up to date.";
        }

        body.put("ok", ok);
        body.put("found", fileValid);
        body.put("fileValid", fileValid);
        body.put("restoreVerified", restorePassed);
        body.put("finishedAt", finished == null ? null : finished.toString());
        body.put("finishedAtDisplay", formatBackupTime(latest.getFinishedAt()));
        body.put("location", latest.getLocation());
        body.put("sizeBytes", latest.getSizeBytes());
        body.put("message", message);
        return body;
    }

    private void beginBackupWork() {
        synchronized (backupLock) {
            if (backupRunning) {
                throw new IllegalStateException("A backup is already running.");
            }
            backupRunning = true;
        }
    }

    private void endBackupWork() {
        synchronized (backupLock) {
            backupRunning = false;
        }
    }

    private void verifyLiveConnectivity() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
        }
    }

    private static String formatBackupTime(LocalDateTime when) {
        if (when == null) {
            return null;
        }
        return when.format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a").withLocale(Locale.US));
    }

    private String dumpDatabase(Path temp) throws Exception {
        if (jdbcUrl.startsWith("jdbc:h2:")) {
            Path target = temp.resolve("database.zip");
            String sqlPath = target.toAbsolutePath().toString().replace('\\', '/').replace("'", "''");
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("BACKUP TO '" + sqlPath + "'");
            }
            return "database.zip";
        }
        if (jdbcUrl.startsWith("jdbc:mysql:")) {
            BackupRules.MysqlTarget target = BackupRules.parseMysqlUrl(jdbcUrl);
            Path sql = temp.resolve("database.sql");
            Path defaults = writeDefaults(target);
            try {
                List<String> command = BackupRules.mysqlDumpCommand(mysqldumpBinary(), defaults.toString(), target.database());
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.redirectOutput(sql.toFile());
                builder.redirectError(ProcessBuilder.Redirect.DISCARD);
                int code = builder.start().waitFor();
                if (code != 0 || !Files.isRegularFile(sql) || Files.size(sql) < 32) {
                    throw new IOException("mysqldump did not create a database file. Set BAYPORT_MYSQLDUMP if it is not on PATH.");
                }
            } finally {
                deleteFile(defaults);
            }
            return "database.sql";
        }
        throw new IllegalStateException("This database type cannot be backed up.");
    }

    private String testRestore(Path databaseFile, String entry) throws Exception {
        if ("database.zip".equals(entry)) {
            Path restored = Files.createTempDirectory("bayport-restore-check-");
            try {
                Class<?> restoreTool = Class.forName("org.h2.tools.Restore");
                restoreTool.getMethod("execute", String.class, String.class, String.class)
                        .invoke(null, databaseFile.toString(), restored.toString(), "check");
                try (Connection connection = java.sql.DriverManager.getConnection(
                        "jdbc:h2:file:" + restored.resolve("check").toAbsolutePath().toString().replace('\\', '/')
                                + ";IFEXISTS=TRUE",
                        "sa",
                        dbPassword);
                     Statement statement = connection.createStatement()) {
                    statement.execute("SELECT 1");
                }
                return "PASSED";
            } finally {
                deleteTree(restored);
            }
        }
        if (verifyDatabase.isBlank()) {
            return "INCOMPLETE";
        }
        BackupRules.MysqlTarget target = BackupRules.parseMysqlUrl(jdbcUrl);
        if (verifyDatabase.equalsIgnoreCase(target.database())) {
            throw new IllegalStateException("BAYPORT_BACKUP_VERIFY_DATABASE must be a different database from the live one.");
        }
        Path defaults = writeDefaults(target);
        try {
            runMysql(mysqlBinary(), defaults, verifyDatabase, databaseFile);
            return "PASSED";
        } finally {
            deleteFile(defaults);
        }
    }

    private void runMysql(String program, Path defaults, String database, Path sql) throws Exception {
        List<String> command = List.of(program, "--defaults-extra-file=" + defaults, database);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectInput(sql.toFile());
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        int code = builder.start().waitFor();
        if (code != 0) {
            throw new IOException("mysql import failed. Set BAYPORT_MYSQL if the client is not on PATH.");
        }
    }

    private Path writeDefaults(BackupRules.MysqlTarget target) throws IOException {
        Path file = Files.createTempFile("bayport-mysql-", ".cnf");
        String text = "[client]\nuser=" + dbUser + "\npassword=" + dbPassword
                + "\nhost=" + target.host() + "\nport=" + target.port() + "\n";
        Files.writeString(file, text);
        return file;
    }

    private String mysqldumpBinary() {
        return mysqldump.isBlank() ? "mysqldump" : mysqldump;
    }

    private String mysqlBinary() {
        return mysqlProgram.isBlank() ? "mysql" : mysqlProgram;
    }

    private void copyUploads(Path destination) throws IOException {
        Path source = Path.of(uploadDir);
        if (!Files.isDirectory(source)) {
            Files.createDirectories(destination);
            return;
        }
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path relative = source.relativize(path);
                if (relative.toString().contains("..")) {
                    continue;
                }
                Path target = destination.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target);
                }
            }
        }
    }

    private void copyToExternal(Path zip) throws IOException {
        if (externalDir == null || externalDir.isBlank()) {
            return;
        }
        Path dir = Path.of(externalDir.trim());
        Files.createDirectories(dir);
        Files.copy(zip, dir.resolve(zip.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private void prune(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<String> ids;
        try (Stream<Path> files = Files.list(dir)) {
            ids = files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("bayport-") && name.endsWith(".zip"))
                    .map(name -> name.substring(0, name.length() - 4))
                    .filter(id -> BackupRules.BACKUP_ID.matcher(id).matches())
                    .sorted(Comparator.reverseOrder())
                    .toList();
        }
        if (ids.isEmpty()) {
            return;
        }

        Map<String, String> reasonById = new HashMap<>();
        for (BackupRun run : backupRunRepository.findAll()) {
            if (run.getLocation() == null || run.getLocation().isBlank()) {
                continue;
            }
            if (!"SUCCESS".equalsIgnoreCase(run.getStatus())) {
                continue;
            }
            reasonById.putIfAbsent(run.getLocation(), run.getMessage());
        }

        List<BackupRules.RetentionCandidate> candidates = new ArrayList<>();
        for (String id : ids) {
            candidates.add(new BackupRules.RetentionCandidate(
                    id,
                    BackupRules.parseIdTimestamp(id),
                    BackupRules.isPreservedReason(reasonById.get(id))
            ));
        }

        List<String> toDelete;
        if (keepDaily > 0 || keepWeekly > 0) {
            toDelete = BackupRules.retentionIdsToDelete(candidates, keepDaily, keepWeekly);
            log.info("Backup retention (daily={}, weekly={}) would remove {} of {} files",
                    keepDaily, keepWeekly, toDelete.size(), ids.size());
        } else {
            toDelete = BackupRules.idsToDelete(ids, keep);
        }

        for (String id : toDelete) {
            boolean removed = Files.deleteIfExists(dir.resolve(id + ".zip"));
            if (removed) {
                log.info("Pruned backup {}", id);
            }
        }
    }

    private Path extractDatabaseSql(Path zipPath) throws IOException {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipPath.toFile())) {
            ZipEntry entry = zip.getEntry("database.sql");
            if (entry == null) {
                throw new IOException("This backup has no MySQL dump.");
            }
            Path sql = Files.createTempFile("bayport-restore-", ".sql");
            try (InputStream input = zip.getInputStream(entry); OutputStream output = Files.newOutputStream(sql)) {
                input.transferTo(output);
            }
            return sql;
        }
    }

    private static void zipDirectory(Path source, Path zip) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip));
             Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                String name = source.relativize(path).toString().replace('\\', '/');
                if (name.contains("..")) {
                    continue;
                }
                output.putNextEntry(new ZipEntry(name));
                Files.copy(path, output);
                output.closeEntry();
            }
        }
    }

    private void notifyAdmins(String subject, String body) {
        try {
            for (User user : userRepository.findAll()) {
                if (user.getEmail() == null || user.getEmail().isBlank() || !user.isActive() || !isAdmin(user)) {
                    continue;
                }
                emailService.sendSimpleEmail(user.getEmail(), subject, body);
            }
        } catch (Exception ex) {
            log.warn("Backup alert email failed: {}", ex.getMessage());
        }
    }

    private static boolean isAdmin(User user) {
        if (user.getRole() != null && user.getRole().equalsIgnoreCase("admin")) {
            return true;
        }
        return user.getRoles() != null && user.getRoles().stream().anyMatch(role -> {
            String name = role.getName() == null ? "" : role.getName().toUpperCase();
            return "ROLE_ADMIN".equals(name) || "ADMIN".equals(name);
        });
    }

    private static String clip(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String text = value.replace('\n', ' ').replace('\r', ' ');
        return text.length() <= 480 ? text : text.substring(0, 480);
    }

    private static void deleteFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The temp file is outside the clinic records.
        }
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // A leftover temp folder does not change clinic records.
        }
    }
}
