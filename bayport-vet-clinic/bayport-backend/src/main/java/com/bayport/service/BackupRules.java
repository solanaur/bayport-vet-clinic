package com.bayport.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Rules for clinic backups. File bytes are checked here. The service writes the files.
 */
public final class BackupRules {

    public static final Duration STALE_AFTER = Duration.ofHours(24);
    public static final Pattern BACKUP_ID = Pattern.compile("bayport-\\d{8}-\\d{6}");

    private BackupRules() {}

    public static Path resolveDirectory(String configured) {
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim());
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return Path.of("C:\\Bayport\\backups");
        }
        return Path.of(System.getProperty("user.home"), "Bayport", "backups");
    }

    public static boolean stale(Instant lastSuccess, Instant now) {
        if (lastSuccess == null || now == null) {
            return true;
        }
        return lastSuccess.isBefore(now.minus(STALE_AFTER));
    }

    public static String confirmPhrase(String backupId) {
        requireBackupId(backupId);
        return "RESTORE " + backupId;
    }

    public static void requireConfirm(String backupId, String typed) {
        String expected = confirmPhrase(backupId);
        if (typed == null || !expected.equals(typed.trim())) {
            throw new IllegalArgumentException("Type " + expected + " to restore this backup.");
        }
    }

    public static void requireBackupId(String backupId) {
        if (backupId == null || !BACKUP_ID.matcher(backupId).matches()) {
            throw new IllegalArgumentException("Unknown backup.");
        }
    }

    public static List<String> idsToDelete(List<String> newestFirst, int keep) {
        int retain = Math.max(keep, 1);
        if (newestFirst.size() <= retain) {
            return List.of();
        }
        return List.copyOf(newestFirst.subList(retain, newestFirst.size()));
    }

    public static String sha256(InputStream input) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new IOException("Could not hash the backup.", ex);
        }
    }

    public static String sha256(byte[] bytes) throws IOException {
        return sha256(new java.io.ByteArrayInputStream(bytes));
    }

    public static void verifyDatabaseEntry(Path zipPath, String databaseEntry, String expectedSha256) throws IOException {
        if (zipPath == null || !Files.isRegularFile(zipPath) || Files.size(zipPath) < 32) {
            throw new IOException("Backup file is missing or too small.");
        }
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zip.getEntry(databaseEntry);
            if (entry == null) {
                throw new IOException("Backup is missing the database file.");
            }
            try (InputStream input = zip.getInputStream(entry)) {
                String actual = sha256(input);
                if (!actual.equalsIgnoreCase(expectedSha256)) {
                    throw new IOException("Backup checksum does not match.");
                }
            }
        }
    }

    public static void verifyArchive(Path zipPath) throws IOException {
        if (zipPath == null || !Files.isRegularFile(zipPath) || Files.size(zipPath) < 32) {
            throw new IOException("Backup file is missing or too small.");
        }
        String entryName = null;
        String expected = null;
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            ZipEntry manifest = zip.getEntry("manifest.txt");
            if (manifest == null) {
                throw new IOException("Backup is missing manifest.txt.");
            }
            try (InputStream input = zip.getInputStream(manifest)) {
                for (String line : new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                    int split = line.indexOf('=');
                    if (split < 1) {
                        continue;
                    }
                    String key = line.substring(0, split).trim();
                    String value = line.substring(split + 1).trim();
                    if ("databaseEntry".equals(key)) {
                        entryName = value;
                    } else if ("databaseSha256".equals(key)) {
                        expected = value;
                    }
                }
            }
        }
        if (entryName == null || entryName.contains("..") || entryName.contains("/") || entryName.contains("\\")
                || expected == null || expected.isBlank()) {
            throw new IOException("Backup manifest is not valid.");
        }
        verifyDatabaseEntry(zipPath, entryName, expected);
    }

    public static List<String> mysqlDumpCommand(String program, String defaultsFile, String database) {
        if (database == null || database.isBlank() || database.contains(" ") || database.contains(";")) {
            throw new IllegalArgumentException("Database name is not valid.");
        }
        List<String> command = new ArrayList<>();
        command.add(program);
        command.add("--defaults-extra-file=" + defaultsFile);
        command.add("--single-transaction");
        command.add("--routines");
        command.add(database);
        String joined = String.join(" ", command).toLowerCase(Locale.ROOT);
        if (joined.contains("password=")) {
            throw new IllegalStateException("The dump command must not contain a password.");
        }
        return List.copyOf(command);
    }

    public static MysqlTarget parseMysqlUrl(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("Not a MySQL URL.");
        }
        String rest = jdbcUrl.substring("jdbc:mysql://".length());
        int query = rest.indexOf('?');
        if (query >= 0) {
            rest = rest.substring(0, query);
        }
        int slash = rest.indexOf('/');
        String hostPort = slash >= 0 ? rest.substring(0, slash) : rest;
        String database = slash >= 0 ? rest.substring(slash + 1) : "";
        String host = hostPort;
        int port = 3306;
        int colon = hostPort.lastIndexOf(':');
        if (colon > 0 && colon < hostPort.length() - 1) {
            host = hostPort.substring(0, colon);
            port = Integer.parseInt(hostPort.substring(colon + 1));
        }
        if (database.isBlank()) {
            throw new IllegalArgumentException("MySQL URL is missing the database name.");
        }
        return new MysqlTarget(host, port, database);
    }

    public record MysqlTarget(String host, int port, String database) {}
}
