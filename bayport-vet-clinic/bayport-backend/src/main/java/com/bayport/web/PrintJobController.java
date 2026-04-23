package com.bayport.web;

import com.bayport.entity.PrintJob;
import com.bayport.service.PrintJobService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/print-jobs")
public class PrintJobController {
    private final PrintJobService printJobService;

    public PrintJobController(PrintJobService printJobService) {
        this.printJobService = printJobService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('FRONT_OFFICE','RECEPTIONIST','PHARMACIST','ADMIN','VET','STAFF')")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, String> body) {
        PrintJob job = printJobService.enqueue(body.get("branchCode"), body.get("deviceName"), body.get("receiptHtml"));
        return ResponseEntity.ok(Map.of("jobId", job.getId(), "status", job.getStatus()));
    }

    @GetMapping("/poll")
    @PreAuthorize("hasAnyRole('FRONT_OFFICE','RECEPTIONIST','PHARMACIST','ADMIN','VET','STAFF')")
    public ResponseEntity<List<PrintJob>> poll(@RequestParam String branchCode) {
        return ResponseEntity.ok(printJobService.pollQueued(branchCode));
    }

    @PostMapping("/{id}/processing")
    @PreAuthorize("hasAnyRole('FRONT_OFFICE','RECEPTIONIST','PHARMACIST','ADMIN','VET','STAFF')")
    public ResponseEntity<PrintJob> markProcessing(@PathVariable Long id) {
        return ResponseEntity.ok(printJobService.markProcessing(id));
    }

    @PostMapping("/{id}/printed")
    @PreAuthorize("hasAnyRole('FRONT_OFFICE','RECEPTIONIST','PHARMACIST','ADMIN','VET','STAFF')")
    public ResponseEntity<PrintJob> markPrinted(@PathVariable Long id) {
        return ResponseEntity.ok(printJobService.markPrinted(id));
    }

    @PostMapping("/{id}/failed")
    @PreAuthorize("hasAnyRole('FRONT_OFFICE','RECEPTIONIST','PHARMACIST','ADMIN','VET','STAFF')")
    public ResponseEntity<PrintJob> markFailed(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(printJobService.markFailed(id, body == null ? null : body.get("error")));
    }
}
