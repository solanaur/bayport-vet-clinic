package com.bayport.web;

import com.bayport.entity.Owner;
import com.bayport.repository.OwnerRepository;
import com.bayport.service.AuditLogService;
import com.bayport.service.SmsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final SmsService smsService;
    private final OwnerRepository ownerRepository;
    private final AuditLogService auditLogService;

    public AnnouncementController(SmsService smsService, OwnerRepository ownerRepository, AuditLogService auditLogService) {
        this.smsService = smsService;
        this.ownerRepository = ownerRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/sms/status")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> status() {
        Map<String, Object> body = new HashMap<>();
        body.put("configured", smsService.isConfigured());
        body.put("provider", smsService.describeProvider());
        long withPhone = ownerRepository.findAll().stream()
                .filter(owner -> owner.getPhone() != null && !owner.getPhone().isBlank())
                .count();
        body.put("ownersWithPhone", withPhone);
        return body;
    }

    @PostMapping("/sms")
    @PreAuthorize("hasAnyRole('ADMIN','VET','FRONT_OFFICE','RECEPTIONIST','STAFF')")
    public ResponseEntity<?> send(@RequestBody Map<String, String> body) {
        String message = body == null ? "" : String.valueOf(body.getOrDefault("message", "")).trim();
        if (message.length() < 4) {
            return ResponseEntity.badRequest().body(Map.of("error", "Write the announcement first."));
        }
        if (message.length() > 480) {
            return ResponseEntity.badRequest().body(Map.of("error", "Announcement must be 480 characters or less."));
        }
        if (!smsService.isConfigured()) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "SMS is not configured yet. Set BAYPORT_SMS_ENABLED and the provider API key, then restart.",
                    "configured", false));
        }

        int sent = 0;
        int failed = 0;
        int skipped = 0;
        List<Owner> owners = ownerRepository.findAll();
        for (Owner owner : owners) {
            if (owner.getPhone() == null || owner.getPhone().isBlank()) {
                skipped++;
                continue;
            }
            SmsService.SmsResult result = smsService.sendAnnouncement(owner.getPhone(), message);
            if (result.accepted()) {
                sent++;
            } else {
                failed++;
            }
        }
        auditLogService.log("SMS_ANNOUNCEMENT", "Owner", "all",
                "SMS announcement sent=" + sent + " failed=" + failed + " skipped=" + skipped, null);
        Map<String, Object> response = new HashMap<>();
        response.put("sent", sent);
        response.put("failed", failed);
        response.put("skipped", skipped);
        response.put("configured", true);
        return ResponseEntity.ok(response);
    }
}
