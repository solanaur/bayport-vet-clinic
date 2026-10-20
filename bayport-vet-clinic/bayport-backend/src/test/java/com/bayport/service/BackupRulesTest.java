package com.bayport.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupRulesTest {

    @Test
    void blankBackupDirectoryStaysOutsideTheAppFolder() {
        String dir = BackupRules.resolveDirectory("").toString();
        assertFalse(dir.contains("bayport-backend"));
        assertTrue(dir.contains("Bayport"));
    }

    @Test
    void backupOlderThan24HoursIsStale() {
        Instant now = Instant.parse("2026-10-15T08:00:00Z");
        assertTrue(BackupRules.stale(null, now));
        assertTrue(BackupRules.stale(now.minus(BackupRules.STALE_AFTER).minusSeconds(1), now));
        assertFalse(BackupRules.stale(now.minusSeconds(60), now));
    }

    @Test
    void restoreRequiresTheExactPhrase() {
        assertEquals("RESTORE bayport-20261015-021500", BackupRules.confirmPhrase("bayport-20261015-021500"));
        BackupRules.requireConfirm("bayport-20261015-021500", "RESTORE bayport-20261015-021500");
        assertThrows(IllegalArgumentException.class,
                () -> BackupRules.requireConfirm("bayport-20261015-021500", "yes"));
        assertThrows(IllegalArgumentException.class,
                () -> BackupRules.requireBackupId("../bayport-20261015-021500"));
    }

    @Test
    void retentionKeepsTheNewestFiles() {
        List<String> newestFirst = List.of("bayport-20261015-030000", "bayport-20261014-030000", "bayport-20261013-030000");
        assertEquals(List.of("bayport-20261013-030000"), BackupRules.idsToDelete(newestFirst, 2));
        assertTrue(BackupRules.idsToDelete(newestFirst, 14).isEmpty());
    }

    @Test
    void archiveChecksumMustMatchTheManifest() throws Exception {
        byte[] database = "CREATE TABLE pets (id INT);".getBytes(StandardCharsets.UTF_8);
        String hash = BackupRules.sha256(database);
        var zip = Files.createTempFile("bayport-backup-test", ".zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("manifest.txt"));
            output.write(("databaseEntry=database.sql\ndatabaseSha256=" + hash + "\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("database.sql"));
            output.write(database);
            output.closeEntry();
        }
        BackupRules.verifyArchive(zip);
        Files.deleteIfExists(zip);
    }

    @Test
    void mysqlCommandDoesNotCarryThePassword() {
        List<String> command = BackupRules.mysqlDumpCommand("mysqldump", "C:/Bayport/config/client.cnf", "bayport_db");
        assertFalse(String.join(" ", command).toLowerCase().contains("password"));
        assertEquals("bayport_db", command.get(command.size() - 1));
        BackupRules.MysqlTarget target = BackupRules.parseMysqlUrl("jdbc:mysql://127.0.0.1:3306/bayport_db?useSSL=false");
        assertEquals("127.0.0.1", target.host());
        assertEquals(3306, target.port());
        assertEquals("bayport_db", target.database());
    }

    @Test
    void linkedInvoiceOrPrescriptionCannotBePurged() {
        assertThrows(IllegalStateException.class, () -> DeletionGuard.requireNoInvoiceOrPrescription(1, 0, "pet"));
        assertThrows(IllegalStateException.class, () -> DeletionGuard.requireNoInvoiceOrPrescription(0, 2, "pet"));
        DeletionGuard.requireNoInvoiceOrPrescription(0, 0, "pet");
    }
}
