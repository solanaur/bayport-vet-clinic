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
import java.util.List;
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
    private final String jdbcUrl;
    private final String dbUser;
    private final String dbPassword;
    private final String mysqldump;
    private final String mysqlProgram;
    private final String uploadDir;
    private final String verifyDatabase;
    private Instant lastStaleAlert = Instant.EPOCH;

    public ClinicBackupService(DataSource dataSource,
                               BackupRunRepository backupRunRepository,
                               UserRepository userRepository,
                               EmailService emailService,
                               @Value("${bayport.backup.dir:}") String backupDir,
                               @Value("${bayport.backup.external-dir:}") String externalDir,
                               @Value("${bayport.backup.keep:14}") int keep,
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
        createBackup("scheduled");
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

    public synchronized BackupRun createBackup(String reason) {
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
        try (Stream<Path> files = Files.list(dir)) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".zip")).toList()) {
                String name = file.getFileName().toString();
                String id = name.substring(0, name.length() - 4);
                if (!BackupRules.BACKUP_ID.matcher(id).matches()) {
                    continue;
                }
                rows.add(Map.of(
                        "id", id,
                        "sizeBytes", Files.size(file),
                        "confirm", BackupRules.confirmPhrase(id)
                ));
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

    public synchronized void restore(String backupId, String confirm) throws Exception {
        BackupRules.requireConfirm(backupId, confirm);
        if (!jdbcUrl.startsWith("jdbc:mysql:")) {
            throw new IllegalStateException(
                    "Stop the server before restoring the desktop database, then replace the database file from the backup zip.");
        }
        createBackup("safety-before-restore");
        Path zip = zipFor(backupId);
        BackupRules.verifyArchive(zip);
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
        log.info("Clinic database restored from {}", backupId);
    }

    public Map<String, Object> status() {
        BackupRun latest = backupRunRepository.findFirstByStatusOrderByFinishedAtDesc("SUCCESS");
        Instant finished = latest == null || latest.getFinishedAt() == null
                ? null
                : latest.getFinishedAt().atZone(ZoneId.systemDefault()).toInstant();
        boolean fresh = !BackupRules.stale(finished, Instant.now());
        boolean verified = latest != null && "PASSED".equals(latest.getVerifyStatus());
        boolean ok = fresh && verified;
        String message = "Backups are up to date.";
        if (latest == null) {
            message = "No successful backup has finished yet.";
        } else if (!fresh) {
            message = "The last successful backup is older than 24 hours.";
        } else if (!verified) {
            message = "The last backup was saved, but the restore test has not passed.";
        }
        return Map.of("ok", ok, "message", message);
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
        for (String id : BackupRules.idsToDelete(ids, keep)) {
            Files.deleteIfExists(dir.resolve(id + ".zip"));
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
