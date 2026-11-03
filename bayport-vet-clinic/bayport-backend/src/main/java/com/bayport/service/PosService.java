package com.bayport.service;

import com.bayport.dto.PendingCartRequest;
import com.bayport.dto.PosCheckoutRequest;
import com.bayport.dto.PosCheckoutResponse;
import com.bayport.dto.PosLineRequest;
import com.bayport.dto.PosReceiptDto;
import com.bayport.entity.Appointment;
import com.bayport.entity.BillingLine;
import com.bayport.entity.InventoryItem;
import com.bayport.entity.BillingRecord;
import com.bayport.entity.Pet;
import com.bayport.entity.Prescription;
import com.bayport.entity.Sale;
import com.bayport.entity.SaleLine;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.bayport.config.InventoryCatalogImporter;
import com.bayport.repository.BillingRecordRepository;
import com.bayport.repository.PetRepository;
import com.bayport.repository.PrescriptionRepository;
import com.bayport.repository.SaleLineRepository;
import com.bayport.repository.SaleRepository;
import com.bayport.util.MoneyUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class PosService {

    private final InventoryService inventoryService;
    private final SalesService salesService;
    private final PrescriptionRepository prescriptionRepository;
    private final PetRepository petRepository;
    private final BayportService bayportService;
    private final InventoryCatalogImporter inventoryCatalogImporter;
    private final SaleLineRepository saleLineRepository;
    private final SaleRepository saleRepository;
    private final PosSaleLineBackfillService posSaleLineBackfillService;
    private final BillingRecordRepository billingRecordRepository;

    public PosService(InventoryService inventoryService,
                      SalesService salesService,
                      PrescriptionRepository prescriptionRepository,
                      PetRepository petRepository,
                      BayportService bayportService,
                      InventoryCatalogImporter inventoryCatalogImporter,
                      SaleLineRepository saleLineRepository,
                      SaleRepository saleRepository,
                      PosSaleLineBackfillService posSaleLineBackfillService,
                      BillingRecordRepository billingRecordRepository) {
        this.inventoryService = inventoryService;
        this.salesService = salesService;
        this.prescriptionRepository = prescriptionRepository;
        this.petRepository = petRepository;
        this.bayportService = bayportService;
        this.inventoryCatalogImporter = inventoryCatalogImporter;
        this.saleLineRepository = saleLineRepository;
        this.saleRepository = saleRepository;
        this.posSaleLineBackfillService = posSaleLineBackfillService;
        this.billingRecordRepository = billingRecordRepository;
    }

    /**
     * Ignore a client-supplied appointment id unless it matches the pet on this sale.
     * Prevents stale browser session keys from affecting checkout (older servers threw here).
     */
    private Long effectiveAppointmentIdForCheckout(Long appointmentId, Long salePetId) {
        if (appointmentId == null || salePetId == null) {
            return null;
        }
        Optional<Appointment> appt = bayportService.getAppointmentById(appointmentId);
        if (appt.isEmpty()) {
            return null;
        }
        Long apptPetId = appt.get().getPetId();
        if (apptPetId == null || !salePetId.equals(apptPetId)) {
            return null;
        }
        return appointmentId;
    }

    /**
     * Saves cart lines as a Pending invoice. Does not create a Sale, does not count as
     * collected revenue, and does not deduct inventory stock.
     */
    @Transactional(rollbackFor = Exception.class)
    public BillingRecord savePending(PendingCartRequest request) {
        if (request.getPetId() == null) {
            throw new IllegalArgumentException("Select a registered pet to save a pending invoice");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }
        Pet pet = petRepository.findById(request.getPetId())
                .orElseThrow(() -> new IllegalArgumentException("Pet not found"));

        BillingRecord invoice = new BillingRecord();
        invoice.setPetId(pet.getId());
        invoice.setPetName(pet.getName());
        invoice.setOwnerId(pet.getOwnerId());
        invoice.setOwnerName(pet.getOwner());
        invoice.setStatus(BillingRecord.Status.PENDING);
        invoice.setPaidAt(null);
        invoice.setIssuedAt(java.time.LocalDateTime.now());
        invoice.setCreatedBy(currentUsername());
        invoice.setReferenceType("POS_PENDING");
        invoice.setDiscountAmount(BigDecimal.ZERO);

        int order = 0;
        List<String> descParts = new ArrayList<>();
        for (PosLineRequest line : request.getLines()) {
            if (line.getBillingRecordId() != null) {
                throw new IllegalArgumentException(
                        "Remove existing pending invoices from the cart before saving a new pending bill");
            }
            int q = line.getQuantity() != null ? line.getQuantity() : 1;
            if (q < 1) {
                throw new IllegalArgumentException("Quantity must be at least 1");
            }

            BillingLine bl = new BillingLine();
            bl.setLineOrder(order++);
            bl.setQuantity(q);

            if (line.getCustomAmount() != null && line.getCustomName() != null && !line.getCustomName().isBlank()) {
                BigDecimal unit = MoneyUtils.normalize(line.getCustomAmount());
                if (unit.signum() <= 0) {
                    throw new IllegalArgumentException("Missing or invalid price for: " + line.getCustomName());
                }
                bl.setServiceName(line.getCustomName().trim());
                bl.setServiceCost(MoneyUtils.normalize(unit.multiply(BigDecimal.valueOf(q))));
            } else {
                InventoryItem item = resolveLineItem(line);
                BigDecimal unit = line.getUnitPriceOverride() != null
                        ? MoneyUtils.normalize(line.getUnitPriceOverride())
                        : (item.getUnitPrice() != null ? MoneyUtils.normalize(item.getUnitPrice()) : BigDecimal.ZERO);
                if (unit.signum() <= 0) {
                    throw new IllegalArgumentException("Missing or invalid price for: " + item.getName());
                }
                bl.setServiceName(item.getName() != null ? item.getName() : "Item");
                bl.setServiceCost(MoneyUtils.normalize(unit.multiply(BigDecimal.valueOf(q))));
                bl.setInventoryItemId(item.getId());
            }
            invoice.addLine(bl);
            descParts.add(bl.getServiceName() + (q > 1 ? " ×" + q : ""));
        }

        BigDecimal discount = request.getDiscountAmount() != null
                ? MoneyUtils.normalize(request.getDiscountAmount())
                : BigDecimal.ZERO;
        if (discount.signum() < 0) {
            throw new IllegalArgumentException("Discount cannot be negative");
        }
        invoice.setDiscountAmount(discount);
        invoice.refreshTotalFromLines();
        if (discount.compareTo(invoice.getSubtotalAmount()) > 0) {
            throw new IllegalArgumentException("Discount cannot exceed the invoice amount");
        }
        invoice.setDescription(summarizePendingDescription(descParts));
        return billingRecordRepository.save(invoice);
    }

    private static String summarizePendingDescription(List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return "Pending charges";
        }
        String joined = String.join(", ", parts);
        if (joined.length() > 240) {
            return joined.substring(0, 237) + "...";
        }
        return joined;
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()
                || "anonymousUser".equalsIgnoreCase(auth.getName())) {
            return null;
        }
        return auth.getName();
    }

    @Transactional(rollbackFor = Exception.class)
    public PosCheckoutResponse checkout(PosCheckoutRequest request) {
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        String payment = request.getPaymentMethod();
        if (payment == null || payment.isBlank()) {
            payment = "Cash";
        }

        BigDecimal total = BigDecimal.ZERO;
        List<String> parts = new ArrayList<>();
        List<SaleLine> lineEntities = new ArrayList<>();
        Set<Long> billingIdsToSettle = new LinkedHashSet<>();
        Set<Long> stockAlreadyDeducted = new LinkedHashSet<>();

        for (PosLineRequest line : request.getLines()) {
            int q = line.getQuantity();
            if (line.getCustomAmount() != null && line.getCustomName() != null && !line.getCustomName().isBlank()) {
                BigDecimal unit = MoneyUtils.normalize(line.getCustomAmount());
                if (unit.signum() <= 0) {
                    throw new IllegalArgumentException("Missing or invalid price for: " + line.getCustomName());
                }
                BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(q));
                total = total.add(lineTotal);
                parts.add(line.getCustomName() + " ×" + q + " @ " + MoneyUtils.formatPeso(unit));
                SaleLine sl = new SaleLine();
                sl.setLineKind(SaleLine.KIND_PROCEDURE);
                sl.setItemName(line.getCustomName());
                sl.setSku(null);
                sl.setQuantity(q);
                sl.setUnitPrice(unit);
                sl.setLineTotal(MoneyUtils.normalize(lineTotal));
                lineEntities.add(sl);
                if (line.getBillingRecordId() != null) {
                    billingIdsToSettle.add(line.getBillingRecordId());
                }
                continue;
            }

            InventoryItem item = resolveLineItem(line);
            BigDecimal unit;
            if (line.getUnitPriceOverride() != null) {
                unit = MoneyUtils.normalize(line.getUnitPriceOverride());
            } else {
                unit = item.getUnitPrice() != null ? MoneyUtils.normalize(item.getUnitPrice()) : BigDecimal.ZERO;
            }
            if (unit.signum() <= 0) {
                throw new IllegalArgumentException("Missing or invalid price for: " + item.getName());
            }
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(q));
            total = total.add(lineTotal);
            inventoryService.applySaleStock(item, q, stockAlreadyDeducted);
            parts.add(item.getName() + " ×" + q + " @ " + MoneyUtils.formatPeso(unit));

            SaleLine sl = new SaleLine();
            sl.setLineKind(InventoryRules.isService(item) ? SaleLine.KIND_PROCEDURE : SaleLine.KIND_PRODUCT);
            sl.setItemName(item.getName() != null ? item.getName() : "");
            sl.setSku(item.getSku());
            sl.setQuantity(q);
            sl.setUnitPrice(unit);
            sl.setLineTotal(MoneyUtils.normalize(lineTotal));
            lineEntities.add(sl);
            if (line.getBillingRecordId() != null) {
                billingIdsToSettle.add(line.getBillingRecordId());
            }
        }

        total = MoneyUtils.normalize(total);
        BigDecimal discount = request.getDiscountAmount() != null
                ? MoneyUtils.normalize(request.getDiscountAmount())
                : BigDecimal.ZERO;
        if (discount.signum() < 0) {
            throw new IllegalArgumentException("Discount cannot be negative");
        }
        BigDecimal subtotalBeforeDiscount = total;
        if (discount.compareTo(total) > 0) {
            discount = total;
        }
        total = MoneyUtils.normalize(total.subtract(discount));

        String petName = null;
        if (request.getPetId() != null) {
            petName = petRepository.findById(request.getPetId()).map(Pet::getName).orElse("Pet #" + request.getPetId());
        } else {
            petName = "Walk-in";
        }

        String note = "Payment: " + payment;
        if (discount.signum() > 0) {
            note += ". Discount: -" + MoneyUtils.formatPeso(discount);
        }
        note += ". " + String.join(" | ", parts);
        final int maxNote = 12_000;
        if (note.length() > maxNote) {
            note = note.substring(0, maxNote - 3) + "...";
        }
        Sale sale = new Sale();
        sale.setAmount(total);
        sale.setPetId(request.getPetId());
        sale.setPetName(petName);
        sale.setSource("POS");
        sale.setNote(note);
        Sale saved = salesService.recordPosSale(sale);

        for (SaleLine sl : lineEntities) {
            sl.setSale(saved);
            saleLineRepository.save(sl);
        }

        if (!billingIdsToSettle.isEmpty()) {
            Long primaryBillingId = null;
            for (Long billingId : billingIdsToSettle) {
                BillingRecord record = billingRecordRepository.findById(billingId)
                        .orElseThrow(() -> new IllegalArgumentException("Billing record not found: " + billingId));
                if (record.getStatus() == BillingRecord.Status.VOID) {
                    throw new IllegalArgumentException("Cannot collect payment on a void invoice: " + billingId);
                }
                if (record.getStatus() == BillingRecord.Status.PAID) {
                    throw new IllegalArgumentException("Invoice " + billingId + " is already paid");
                }
                if (record.getPetId() != null && request.getPetId() != null && !record.getPetId().equals(request.getPetId())) {
                    throw new IllegalArgumentException("Billing record " + billingId + " does not belong to selected pet");
                }
                BigDecimal lineSubtotal = record.getSubtotalAmount() != null
                        ? record.getSubtotalAmount()
                        : record.getAmount();
                if (lineSubtotal == null) {
                    lineSubtotal = subtotalBeforeDiscount;
                }
                BigDecimal invoiceDiscount = record.getDiscountAmount() != null
                        ? record.getDiscountAmount()
                        : BigDecimal.ZERO;
                // Preserve the invoice's original discount unless checkout applies an additional one.
                BigDecimal appliedDiscount = discount.signum() > 0 ? discount : invoiceDiscount;
                if (appliedDiscount.compareTo(lineSubtotal) > 0) {
                    appliedDiscount = lineSubtotal;
                }
                record.setSubtotalAmount(lineSubtotal);
                record.setDiscountAmount(appliedDiscount);
                record.setAmount(MoneyUtils.normalize(lineSubtotal.subtract(appliedDiscount)));
                record.setStatus(BillingRecord.Status.PAID);
                record.setPaidAt(java.time.LocalDateTime.now());
                deductStockForPendingInventoryLines(record, stockAlreadyDeducted);
                billingRecordRepository.save(record);
                if (primaryBillingId == null) {
                    primaryBillingId = billingId;
                }
            }
            if (primaryBillingId != null) {
                saved.setBillingId(primaryBillingId);
                saleRepository.save(saved);
            }
        } else {
            BillingRecord invoice = new BillingRecord();
            invoice.setPetId(request.getPetId());
            invoice.setPetName(petName);
            if (request.getPetId() != null) {
                petRepository.findById(request.getPetId()).ifPresent(p -> {
                    invoice.setOwnerId(p.getOwnerId());
                    invoice.setOwnerName(p.getOwner());
                });
            } else {
                invoice.setOwnerName("Walk-in customer");
            }
            invoice.setDescription(summarizeLineItems(lineEntities));
            invoice.setSubtotalAmount(subtotalBeforeDiscount);
            invoice.setDiscountAmount(discount);
            invoice.setAmount(total);
            invoice.setStatus(BillingRecord.Status.PAID);
            invoice.setPaidAt(java.time.LocalDateTime.now());
            invoice.setIssuedAt(java.time.LocalDateTime.now());
            invoice.setReferenceType("POS");
            invoice.setReferenceId(saved.getId());
            BillingRecord savedInvoice = billingRecordRepository.save(invoice);
            saved.setBillingId(savedInvoice.getId());
            saleRepository.save(saved);
        }

        if (request.getFulfillPrescriptionIds() != null && !request.getFulfillPrescriptionIds().isEmpty()) {
            Long petId = request.getPetId();
            if (petId == null) {
                throw new IllegalArgumentException("Pet is required when fulfilling prescriptions");
            }
            for (Long rxId : request.getFulfillPrescriptionIds()) {
                Prescription rx = prescriptionRepository.findById(rxId)
                        .orElseThrow(() -> new IllegalArgumentException("Prescription not found: " + rxId));
                if (rx.getPetId() == null || !rx.getPetId().equals(petId)) {
                    throw new IllegalArgumentException("Prescription " + rxId + " does not belong to the selected pet");
                }
                if (rx.isDispensed()) {
                    continue;
                }
                bayportService.dispensePrescription(rxId);
            }
        }

        Long apptToComplete = effectiveAppointmentIdForCheckout(request.getAppointmentId(), request.getPetId());
        bayportService.tryMarkAppointmentDoneAfterPosCheckout(apptToComplete, request.getPetId());

        return new PosCheckoutResponse(saved.getId(), total, note);
    }

    /**
     * Recent POS checkouts for the sales history panel (newest first).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> recentPosSales(int limit) {
        posSaleLineBackfillService.backfillMissingLines();
        int cap = Math.max(1, Math.min(limit, 200));
        Pageable pg = PageRequest.of(0, cap, Sort.Direction.DESC, "occurredAt");
        Page<Sale> page = saleRepository.findRecentPosPage(pg);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Sale s : page.getContent()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("saleId", s.getId() != null ? s.getId() : 0L);
            row.put("occurredAt", s.getOccurredAt() != null ? s.getOccurredAt().toString() : "");
            row.put("petName", s.getPetName() != null && !s.getPetName().isBlank() ? s.getPetName() : "Walk-in");
            row.put("total", s.getAmount());
            row.put("note", s.getNote() != null ? s.getNote() : "");
            List<Map<String, Object>> lines = new ArrayList<>();
            for (SaleLine sl : saleLineRepository.findBySale_IdOrderByIdAsc(s.getId())) {
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("itemName", sl.getItemName());
                l.put("sku", sl.getSku());
                l.put("quantity", sl.getQuantity());
                l.put("unitPrice", sl.getUnitPrice());
                l.put("lineTotal", sl.getLineTotal());
                l.put("lineKind", sl.getLineKind());
                lines.add(l);
            }
            row.put("lines", lines);
            out.add(row);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public PosReceiptDto receiptBySaleId(long saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));
        PosReceiptDto dto = new PosReceiptDto();
        dto.saleId = sale.getId();
        dto.occurredAt = sale.getOccurredAt() != null ? sale.getOccurredAt().toString() : "";
        dto.petName = sale.getPetName() != null && !sale.getPetName().isBlank() ? sale.getPetName() : "Walk-in";
        dto.ownerName = sale.getPetId() != null
                ? petRepository.findById(sale.getPetId()).map(Pet::getOwner).orElse("—")
                : "Walk-in customer";
        dto.total = sale.getAmount();
        dto.note = sale.getNote();
        dto.paymentMethod = extractPaymentMethod(sale.getNote());
        for (SaleLine sl : saleLineRepository.findBySale_IdOrderByIdAsc(sale.getId())) {
            PosReceiptDto.Line line = new PosReceiptDto.Line();
            line.itemName = sl.getItemName();
            line.sku = sl.getSku();
            line.quantity = sl.getQuantity();
            line.unitPrice = sl.getUnitPrice();
            line.lineTotal = sl.getLineTotal();
            dto.lines.add(line);
        }
        return dto;
    }

    private static String extractPaymentMethod(String note) {
        if (note == null) return "Cash";
        String marker = "Payment:";
        int idx = note.indexOf(marker);
        if (idx < 0) return "Cash";
        int from = idx + marker.length();
        int end = note.indexOf('.', from);
        String value = end > from ? note.substring(from, end) : note.substring(from);
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "Cash" : trimmed;
    }

    /** Human-readable invoice description from checkout line items (not a generic POS label). */
    private static String summarizeLineItems(List<SaleLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return "Sale";
        }
        List<String> names = new ArrayList<>();
        for (SaleLine sl : lines) {
            String name = sl.getItemName();
            if (name != null && !name.isBlank()) {
                names.add(name.trim());
            }
        }
        if (names.isEmpty()) {
            return "Sale";
        }
        String joined = String.join(", ", names);
        final int maxLen = 500;
        if (joined.length() > maxLen) {
            return joined.substring(0, maxLen - 3) + "...";
        }
        return joined;
    }

    /**
     * When a previously saved pending invoice (with inventory refs) is paid via custom cart lines,
     * deduct stock now — it was intentionally skipped at Save as Pending.
     * Shares {@code alreadyDeducted} with cart product lines so the same vial is not deducted twice.
     */
    private void deductStockForPendingInventoryLines(BillingRecord record, Set<Long> alreadyDeducted) {
        if (record == null || record.getLines() == null) {
            return;
        }
        for (BillingLine line : record.getLines()) {
            if (line == null || line.getInventoryItemId() == null) {
                continue;
            }
            int q = line.getQuantity() != null && line.getQuantity() > 0 ? line.getQuantity() : 1;
            InventoryItem item = inventoryService.get(line.getInventoryItemId());
            inventoryService.applySaleStock(item, q, alreadyDeducted);
        }
    }

    private InventoryItem resolveLineItem(PosLineRequest line) {
        if (line.getInventoryItemId() != null) {
            return inventoryService.get(line.getInventoryItemId());
        }
        if (line.getSku() != null && !line.getSku().isBlank()) {
            return inventoryCatalogImporter.ensureSkuInInventory(line.getSku().trim());
        }
        throw new IllegalArgumentException("Each line needs inventoryItemId (products) or sku (clinic fees)");
    }
}
