package com.bayport.web;

import com.bayport.entity.BackupRun;
import com.bayport.service.AuditLogService;
import com.bayport.service.ClinicBackupService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BackupController {

    private final ClinicBackupService clinicBackupService;
    private final AuditLogService auditLogService;

    public BackupController(ClinicBackupService clinicBackupService, AuditLogService auditLogService) {
        this.clinicBackupService = clinicBackupService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/backup-status")
    public Map<String, Object> status() {
        return clinicBackupService.status();
    }

    @GetMapping("/admin/backups")
    public ResponseEntity<?> list() {
        try {
            return ResponseEntity.ok(clinicBackupService.listBackups());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Could not list backups."));
        }
    }

    @PostMapping("/admin/backups")
    public ResponseEntity<?> create() {
        if (clinicBackupService.isBackupRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("ok", false, "message", "A backup is already running."));
        }
        BackupRun run;
        try {
            run = clinicBackupService.createBackup("manual");
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("ok", false, "message", ex.getMessage()));
        }
        auditLogService.log("BACKUP_CREATED", "Backup", run.getLocation(), run.getStatus());
        if (!"SUCCESS".equals(run.getStatus()) || run.getLocation() == null || run.getLocation().isBlank()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "ok", false,
                            "message", run.getMessage() == null || run.getMessage().isBlank()
                                    ? "Backup did not finish."
                                    : run.getMessage()
                    ));
        }
        try {
            var zip = clinicBackupService.zipFor(run.getLocation());
            if (!Files.isRegularFile(zip) || !Files.isReadable(zip)) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("ok", false, "message", "Backup file is missing or not readable."));
            }
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "message", "Backup file could not be verified."));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("id", run.getLocation());
        body.put("filename", run.getLocation() + ".zip");
        body.put("sizeBytes", run.getSizeBytes());
        body.put("finishedAt", run.getFinishedAt() == null ? null : run.getFinishedAt().toString());
        body.put("verifyStatus", run.getVerifyStatus());
        body.put("message", "Backup saved as " + run.getLocation() + ".zip");
        return ResponseEntity.ok(body);
    }

    @GetMapping("/admin/backups/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable String id) {
        var zip = clinicBackupService.zipFor(id);
        auditLogService.log("BACKUP_DOWNLOAD", "Backup", id, "Backup downloaded");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + id + ".zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(zip));
    }

    @PostMapping("/admin/backups/{id}/restore")
    public ResponseEntity<Map<String, Object>> restore(@PathVariable String id, @RequestBody Map<String, String> body) {
        try {
            Map<String, Object> result = clinicBackupService.restore(id, body == null ? "" : body.get("confirm"));
            auditLogService.log("BACKUP_RESTORED", "Backup", id, "Database restored from backup");
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", ex.getMessage()));
        } catch (IllegalStateException ex) {
            auditLogService.log("BACKUP_RESTORE_BLOCKED", "Backup", id, ex.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("ok", false, "message", ex.getMessage() == null ? "Restore did not finish." : ex.getMessage()));
        } catch (Exception ex) {
            auditLogService.log("BACKUP_RESTORE_FAILED", "Backup", id, "Restore did not finish");
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("ok", false, "message", ex.getMessage() == null ? "Restore did not finish." : ex.getMessage()));
        }
    }
}
