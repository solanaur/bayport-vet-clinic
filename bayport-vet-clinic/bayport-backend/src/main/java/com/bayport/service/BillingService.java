package com.bayport.service;

import com.bayport.entity.BillingRecord;
import com.bayport.entity.Pet;
import com.bayport.entity.Procedure;
import com.bayport.repository.BillingRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class BillingService {

    private final BillingRecordRepository billingRecordRepository;
    private final SalesService salesService;

    public BillingService(BillingRecordRepository billingRecordRepository,
                          SalesService salesService) {
        this.billingRecordRepository = billingRecordRepository;
        this.salesService = salesService;
    }

    public List<BillingRecord> listAll() {
        return billingRecordRepository.findAll();
    }

    public BillingRecord get(Long id) {
        return billingRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Billing record not found"));
    }

    public BillingRecord create(BillingRecord record) {
        BillingRecord saved = billingRecordRepository.save(record);
        if (saved.getStatus() == BillingRecord.Status.PAID) {
            salesService.recordSale(saved, "Manual", "Manual payment");
        }
        return saved;
    }

    public BillingRecord markPaid(Long id) {
        BillingRecord record = get(id);
        if (record.getStatus() != BillingRecord.Status.PAID) {
            record.setStatus(BillingRecord.Status.PAID);
            record.setPaidAt(LocalDateTime.now());
            billingRecordRepository.save(record);
            salesService.recordSale(record, "Manual", "Marked as paid");
        }
        return record;
    }

    public void delete(Long id) {
        billingRecordRepository.deleteById(id);
    }

    public BillingRecord billProcedure(Pet pet, Procedure procedure) {
        BillingRecord record = new BillingRecord();
        record.setPetId(pet.getId());
        record.setPetName(pet.getName());
        record.setOwnerId(pet.getOwnerId());
        record.setOwnerName(pet.getOwner());
        record.setDescription(procedure.getName());
        record.setAmount(procedure.getCost());
        record.setReferenceType("PROCEDURE");
        record.setReferenceId(procedure.getId());
        record.setStatus(BillingRecord.Status.PAID);
        record.setPaidAt(LocalDateTime.now());
        BillingRecord saved = billingRecordRepository.save(record);
        salesService.recordSale(saved, "Procedure", "Procedure charge");
        return saved;
    }
}

