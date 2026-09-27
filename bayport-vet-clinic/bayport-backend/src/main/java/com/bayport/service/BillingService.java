package com.bayport.service;

import com.bayport.entity.BillingLine;
import com.bayport.entity.BillingRecord;
import com.bayport.entity.Consultation;
import com.bayport.entity.Pet;
import com.bayport.entity.Procedure;
import com.bayport.repository.BillingRecordRepository;
import com.bayport.util.MoneyUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class BillingService {

    private static final DateTimeFormatter CONSULT_DATE = DateTimeFormatter.ofPattern("M/d/yy");

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
        if (record.getSubtotalAmount() == null && record.getAmount() != null) {
            record.setSubtotalAmount(record.getAmount());
        }
        if (record.getDiscountAmount() == null) {
            record.setDiscountAmount(java.math.BigDecimal.ZERO);
        }
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
        return billConsultation(pet, null, List.of(procedure));
    }

    /**
     * Creates one invoice for a consultation (or a standalone visit) with
     * itemized service lines. {@code total_amount} is the SUM of line costs.
     */
    public BillingRecord billConsultation(Pet pet, Consultation consultation, List<Procedure> procedures) {
        List<Procedure> billable = new ArrayList<>();
        if (procedures != null) {
            for (Procedure procedure : procedures) {
                if (procedure != null && procedure.getCost() != null && procedure.getCost().signum() > 0) {
                    billable.add(procedure);
                }
            }
        }
        if (billable.isEmpty()) {
            return null;
        }

        BillingRecord record = new BillingRecord();
        record.setPetId(pet.getId());
        record.setPetName(pet.getName());
        record.setOwnerId(pet.getOwnerId());
        record.setOwnerName(pet.getOwner());
        record.setDiscountAmount(BigDecimal.ZERO);
        record.setStatus(BillingRecord.Status.PENDING);
        record.setPaidAt(null);

        Long consultationId = consultation != null ? consultation.getId() : firstConsultationId(billable);
        record.setConsultationId(consultationId);
        if (consultation != null && consultation.getId() != null) {
            record.setReferenceType("CONSULTATION");
            record.setReferenceId(consultation.getId());
        } else {
            record.setReferenceType("PROCEDURE");
            record.setReferenceId(billable.get(0).getId());
        }

        int order = 0;
        for (Procedure procedure : billable) {
            BillingLine line = new BillingLine();
            line.setConsultationId(consultationId != null ? consultationId : procedure.getConsultationId());
            line.setProcedureId(procedure.getId());
            line.setServiceName(StringUtils.hasText(procedure.getName()) ? procedure.getName() : "Service");
            line.setServiceCost(MoneyUtils.normalize(procedure.getCost()));
            line.setPerformedBy(procedure.getVet());
            line.setLineOrder(order++);
            record.addLine(line);
        }
        record.refreshTotalFromLines();
        record.setDescription(consultationInvoiceDescription(consultation, billable));
        return billingRecordRepository.save(record);
    }

    private static Long firstConsultationId(List<Procedure> procedures) {
        for (Procedure procedure : procedures) {
            if (procedure.getConsultationId() != null) {
                return procedure.getConsultationId();
            }
        }
        return null;
    }

    private static String consultationInvoiceDescription(Consultation consultation, List<Procedure> billable) {
        LocalDate date = consultation != null ? consultation.getConsultDate() : null;
        if (date == null && billable.get(0).getPerformedAt() != null) {
            date = billable.get(0).getPerformedAt();
        }
        String dateLabel = date != null ? CONSULT_DATE.format(date) : "visit";
        if (billable.size() == 1 && consultation == null) {
            return billable.get(0).getName();
        }
        return "Consultation " + dateLabel + " — " + billable.size()
                + (billable.size() == 1 ? " service" : " services");
    }
}

