package com.bayport.service;

import com.bayport.dto.PosCheckoutRequest;
import com.bayport.dto.PosCheckoutResponse;
import com.bayport.dto.PosLineRequest;
import com.bayport.dto.PosReceiptDto;
import com.bayport.dto.PosSaleHistoryDto;
import com.bayport.entity.InventoryItem;
import com.bayport.entity.Pet;
import com.bayport.entity.Prescription;
import com.bayport.entity.Sale;
import com.bayport.entity.SaleLine;
import com.bayport.config.InventoryCatalogImporter;
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
import java.util.List;

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

    public PosService(InventoryService inventoryService,
                      SalesService salesService,
                      PrescriptionRepository prescriptionRepository,
                      PetRepository petRepository,
                      BayportService bayportService,
                      InventoryCatalogImporter inventoryCatalogImporter,
                      SaleLineRepository saleLineRepository,
                      SaleRepository saleRepository,
                      PosSaleLineBackfillService posSaleLineBackfillService) {
        this.inventoryService = inventoryService;
        this.salesService = salesService;
        this.prescriptionRepository = prescriptionRepository;
        this.petRepository = petRepository;
        this.bayportService = bayportService;
        this.inventoryCatalogImporter = inventoryCatalogImporter;
        this.saleLineRepository = saleLineRepository;
        this.saleRepository = saleRepository;
        this.posSaleLineBackfillService = posSaleLineBackfillService;
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

        for (PosLineRequest line : request.getLines()) {
            InventoryItem item = resolveLineItem(line);
            int q = line.getQuantity();
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
            inventoryService.decreaseQuantityForSale(item.getId(), q);
            parts.add(item.getName() + " ×" + q + " @ " + MoneyUtils.formatPeso(unit));

            SaleLine sl = new SaleLine();
            sl.setLineKind(isProcedureLine(item) ? SaleLine.KIND_PROCEDURE : SaleLine.KIND_PRODUCT);
            sl.setItemName(item.getName() != null ? item.getName() : "");
            sl.setSku(item.getSku());
            sl.setQuantity(q);
            sl.setUnitPrice(unit);
            sl.setLineTotal(MoneyUtils.normalize(lineTotal));
            lineEntities.add(sl);
        }

        total = MoneyUtils.normalize(total);

        String petName = null;
        if (request.getPetId() != null) {
            petName = petRepository.findById(request.getPetId()).map(Pet::getName).orElse("Pet #" + request.getPetId());
        }

        String note = "Payment: " + payment + ". " + String.join(" | ", parts);
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

        return new PosCheckoutResponse(saved.getId(), total, note);
    }

    /**
     * Recent POS checkouts for the sales history panel (newest first).
     */
    @Transactional(readOnly = true)
    public List<PosSaleHistoryDto> recentPosSales(int limit) {
        posSaleLineBackfillService.backfillMissingLines();
        int cap = Math.max(1, Math.min(limit, 200));
        Pageable pg = PageRequest.of(0, cap, Sort.Direction.DESC, "occurredAt");
        Page<Sale> page = saleRepository.findRecentPosPage(pg);
        List<PosSaleHistoryDto> out = new ArrayList<>();
        for (Sale s : page.getContent()) {
            PosSaleHistoryDto row = new PosSaleHistoryDto();
            row.saleId = s.getId() != null ? s.getId() : 0L;
            row.occurredAt = s.getOccurredAt() != null ? s.getOccurredAt().toString() : "";
            row.petName = s.getPetName() != null && !s.getPetName().isBlank() ? s.getPetName() : "—";
            row.total = s.getAmount();
            for (SaleLine sl : saleLineRepository.findBySale_IdOrderByIdAsc(s.getId())) {
                PosSaleHistoryDto.Line l = new PosSaleHistoryDto.Line();
                l.itemName = sl.getItemName();
                l.sku = sl.getSku();
                l.quantity = sl.getQuantity();
                l.unitPrice = sl.getUnitPrice();
                l.lineTotal = sl.getLineTotal();
                l.lineKind = sl.getLineKind();
                row.lines.add(l);
            }
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

    private InventoryItem resolveLineItem(PosLineRequest line) {
        if (line.getInventoryItemId() != null) {
            return inventoryService.get(line.getInventoryItemId());
        }
        if (line.getSku() != null && !line.getSku().isBlank()) {
            return inventoryCatalogImporter.ensureSkuInInventory(line.getSku().trim());
        }
        throw new IllegalArgumentException("Each line needs inventoryItemId (products) or sku (clinic fees)");
    }

    private static boolean isProcedureLine(InventoryItem item) {
        String c = item.getCategory();
        return c != null && "SERVICE".equalsIgnoreCase(c.trim());
    }
}
