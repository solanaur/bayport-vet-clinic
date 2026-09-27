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
    @PreAuthorize("hasAnyRole('FRONT_OFFICE','RECEPTIONIST','PHARMACIST','ADMIN')")
    public List<BillingRecord> list() {
        return billingService.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FRONT_OFFICE','RECEPTIONIST','PHARMACIST','ADMIN')")
    public BillingRecord get(@PathVariable Long id) {
        return billingService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('FRONT_OFFICE','RECEPTIONIST','PHARMACIST','ADMIN')")
    public BillingRecord create(@RequestBody BillingRecord record) {
        if (record.getAmount() != null && record.getAmount().signum() < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        return billingService.create(record);
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('FRONT_OFFICE','RECEPTIONIST','PHARMACIST','ADMIN')")
    public BillingRecord markPaid(@PathVariable Long id) {
        return billingService.markPaid(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        billingService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

