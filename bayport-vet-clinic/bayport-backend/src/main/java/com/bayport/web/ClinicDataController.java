package com.bayport.web;

import com.bayport.service.AuditLogService;
import com.bayport.service.ClinicBackupService;
import com.bayport.service.ClinicDataService;
import com.bayport.service.SystemHealthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ClinicDataController {

    private final ClinicDataService clinicDataService;
    private final ClinicBackupService clinicBackupService;
    private final AuditLogService auditLogService;
    private final SystemHealthService systemHealthService;

    public ClinicDataController(ClinicDataService clinicDataService,
                                ClinicBackupService clinicBackupService,
                                AuditLogService auditLogService,
                                SystemHealthService systemHealthService) {
        this.clinicDataService = clinicDataService;
        this.clinicBackupService = clinicBackupService;
        this.auditLogService = auditLogService;
        this.systemHealthService = systemHealthService;
    }

    @GetMapping("/dashboard/alerts")
    public Map<String, Object> alerts() {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.putAll(clinicDataService.alerts());
        body.put("backup", clinicBackupService.status());
        return body;
    }

    @GetMapping("/admin/health")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> adminHealth() {
        return systemHealthService.snapshot();
    }

    @GetMapping(value = "/records/export/{kind}/csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('ADMIN','VET','VETERINARIAN','FRONT_OFFICE','RECEPTIONIST','PHARMACIST')")
    public ResponseEntity<String> exportCsv(@PathVariable String kind) {
        auditLogService.log("EXPORT", "Record", kind, "CSV export");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + kind + ".csv\"")
                .body(clinicDataService.exportCsv(kind));
    }

    @GetMapping(value = "/records/export/{kind}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','VET','VETERINARIAN','FRONT_OFFICE','RECEPTIONIST','PHARMACIST')")
    public ResponseEntity<byte[]> exportPdf(@PathVariable String kind) {
        auditLogService.log("EXPORT", "Record", kind, "PDF export");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + kind + ".pdf\"")
                .body(clinicDataService.exportPdf(kind));
    }

    @GetMapping(value = "/admin/import/{kind}/template", produces = "text/csv")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> template(@PathVariable String kind) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + kind + "-template.csv\"")
                .body(clinicDataService.template(kind));
    }

    @PostMapping("/admin/import/{kind}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> importRows(@PathVariable String kind, @RequestBody ImportRequest request) {
        boolean commit = request != null && request.commit;
        Map<String, Object> result = clinicDataService.importRows(kind, request == null ? List.of() : request.rows, commit);
        if (commit && Boolean.TRUE.equals(result.get("committed"))) {
            auditLogService.log("IMPORT", "Record", kind, "Imported " + result.get("count") + " rows");
        }
        return result;
    }

    public static class ImportRequest {
        public boolean commit;
        public List<Map<String, String>> rows;
    }
}
