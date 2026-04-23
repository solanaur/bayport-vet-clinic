package com.bayport.web;

import com.bayport.entity.BillingRecord;
import com.bayport.service.BillingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FRONT_OFFICE','RECEPTIONIST','PHARMACIST','ADMIN','VET','STAFF')")
    public List<BillingRecord> list() {
        return billingService.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FRONT_OFFICE','RECEPTIONIST','PHARMACIST','ADMIN','VET','STAFF')")
    public BillingRecord get(@PathVariable Long id) {
        return billingService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('FRONT_OFFICE','RECEPTIONIST','PHARMACIST','ADMIN','VET','STAFF')")
    public BillingRecord create(@RequestBody BillingRecord record) {
        return billingService.create(record);
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('FRONT_OFFICE','RECEPTIONIST','PHARMACIST','ADMIN','VET','STAFF')")
    public BillingRecord markPaid(@PathVariable Long id) {
        return billingService.markPaid(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        billingService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

