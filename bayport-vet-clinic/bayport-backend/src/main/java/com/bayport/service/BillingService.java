package com.bayport.service;

import com.bayport.entity.BillingLine;
import com.bayport.entity.BillingRecord;
import com.bayport.entity.Consultation;
import com.bayport.entity.Pet;
import com.bayport.entity.Procedure;
import com.bayport.repository.BillingRecordRepository;
import com.bayport.repository.PetRepository;
import com.bayport.util.MoneyUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final PetRepository petRepository;
    private final SalesService salesService;
    private final AuditLogService auditLogService;

    public BillingService(BillingRecordRepository billingRecordRepository,
                          PetRepository petRepository,
                          SalesService salesService,
                          AuditLogService auditLogService) {
        this.billingRecordRepository = billingRecordRepository;
        this.petRepository = petRepository;
        this.salesService = salesService;
        this.auditLogService = auditLogService;
    }

    public List<BillingRecord> listAll() {
        return billingRecordRepository.findAll().stream()
                .filter(record -> record.getDeleted() == null || !record.getDeleted())
                .toList();
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
        // New invoices start as Pending — creating an invoice is not collected revenue.
        record.setStatus(BillingRecord.Status.PENDING);
        record.setPaidAt(null);
        if (record.getIssuedAt() == null) {
            record.setIssuedAt(LocalDateTime.now());
        }
        if (!StringUtils.hasText(record.getCreatedBy())) {
            record.setCreatedBy(currentUsername());
        }
        enrichFromPet(record);
        BigDecimal subtotal = record.getSubtotalAmount() != null ? record.getSubtotalAmount() : BigDecimal.ZERO;
        BigDecimal discount = record.getDiscountAmount() != null ? record.getDiscountAmount() : BigDecimal.ZERO;
        if (discount.signum() < 0) {
            throw new IllegalArgumentException("Discount cannot be negative");
        }
        if (discount.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("Discount cannot exceed the invoice amount");
        }
        record.setAmount(MoneyUtils.normalize(subtotal.subtract(discount)));
        return billingRecordRepository.save(record);
    }

    /** Fill pet/owner fields from the pet's owner relationship when a petId is present. */
    private void enrichFromPet(BillingRecord record) {
        if (record.getPetId() == null) {
            return;
        }
        petRepository.findById(record.getPetId()).ifPresent(pet -> {
            if (!StringUtils.hasText(record.getPetName())) {
                record.setPetName(pet.getName());
            }
            if (record.getOwnerId() == null) {
                record.setOwnerId(pet.getOwnerId());
            }
            if (!StringUtils.hasText(record.getOwnerName())) {
                record.setOwnerName(pet.getOwner());
            }
        });
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()
                || "anonymousUser".equalsIgnoreCase(auth.getName())) {
            return null;
        }
        return auth.getName();
    }

    public BillingRecord markPaid(Long id) {
        BillingRecord record = get(id);
        if (record.getStatus() == BillingRecord.Status.VOID) {
            throw new IllegalArgumentException("A void invoice cannot be marked paid.");
        }
        if (record.getStatus() != BillingRecord.Status.PAID) {
            record.setStatus(BillingRecord.Status.PAID);
            record.setPaidAt(LocalDateTime.now());
            billingRecordRepository.save(record);
            salesService.recordSale(record, "Manual", "Marked as paid");
        }
        return record;
    }

    public BillingRecord voidInvoice(Long id, String reason) {
        BillingRecord record = get(id);
        String problem = BillingVoidRules.problem(record.getStatus() == null ? "" : record.getStatus().name(), reason);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        record.setStatus(BillingRecord.Status.VOID);
        record.setVoidReason(reason.trim());
        record.setVoidedAt(LocalDateTime.now());
        billingRecordRepository.save(record);
        auditLogService.log("VOID", "Invoice", String.valueOf(id), reason.trim());
        return record;
    }

    public void delete(Long id) {
        BillingRecord record = billingRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        record.setDeleted(true);
        record.setDeletedAt(LocalDateTime.now());
        billingRecordRepository.save(record);
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

