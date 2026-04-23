package com.bayport.service;

import com.bayport.dto.ReportSummary;
import com.bayport.entity.InventoryItem;
import com.bayport.entity.Sale;
import com.bayport.entity.SaleLine;
import com.bayport.repository.InventoryItemRepository;
import com.bayport.repository.PetRepository;
import com.bayport.repository.PrescriptionRepository;
import com.bayport.repository.SaleLineRepository;
import com.bayport.repository.SaleRepository;
import com.bayport.util.MoneyUtils;
import com.bayport.util.PosSaleNoteParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final PetRepository petRepository;
    private final BayportService bayportService;
    private final PrescriptionRepository prescriptionRepository;
    private final SaleRepository saleRepository;
    private final SaleLineRepository saleLineRepository;
    private final PosSaleLineBackfillService posSaleLineBackfillService;
    private final InventoryItemRepository inventoryItemRepository;

    public ReportService(PetRepository petRepository,
                         BayportService bayportService,
                         PrescriptionRepository prescriptionRepository,
                         SaleRepository saleRepository,
                         SaleLineRepository saleLineRepository,
                         PosSaleLineBackfillService posSaleLineBackfillService,
                         InventoryItemRepository inventoryItemRepository) {
        this.petRepository = petRepository;
        this.bayportService = bayportService;
        this.prescriptionRepository = prescriptionRepository;
        this.saleRepository = saleRepository;
        this.saleLineRepository = saleLineRepository;
        this.posSaleLineBackfillService = posSaleLineBackfillService;
        this.inventoryItemRepository = inventoryItemRepository;
    }

    public ReportSummary summarize(LocalDate start, LocalDate end, String period) {
        ReportSummary summary = new ReportSummary();
        summary.period = period != null ? period : "day";
        summary.from = start != null ? start.toString() : LocalDate.now().toString();
        summary.to = end != null ? end.toString() : LocalDate.now().toString();

        try {
            summary.appointmentsDone = (int) bayportService.getAllAppointments().stream()
                    .filter(a -> a != null && "Done".equalsIgnoreCase(a.getStatus()))
                    .filter(a -> a.getCompletedAt() != null)
                    .filter(a -> !a.getCompletedAt().isBefore(start) && !a.getCompletedAt().isAfter(end))
                    .count();
        } catch (Exception e) {
            summary.appointmentsDone = 0;
        }

        List<com.bayport.entity.Prescription> dispensedPrescriptions = new ArrayList<>();
        try {
            dispensedPrescriptions = prescriptionRepository.findAll().stream()
                    .filter(p -> p != null && p.isDispensed() && p.getDispensedAt() != null)
                    .filter(p -> !p.getDispensedAt().isBefore(start) && !p.getDispensedAt().isAfter(end))
                    .toList();
            summary.prescriptionsDispensed = dispensedPrescriptions.size();
        } catch (Exception e) {
            summary.prescriptionsDispensed = 0;
            dispensedPrescriptions = new ArrayList<>();
        }

        try {
            summary.petsAdded = (int) petRepository.findAll().stream()
                    .filter(p -> p != null && p.getCreatedAt() != null)
                    .filter(p -> !p.getCreatedAt().toLocalDate().isBefore(start)
                            && !p.getCreatedAt().toLocalDate().isAfter(end))
                    .count();
        } catch (Exception e) {
            summary.petsAdded = 0;
        }

        try {
            summary.events = bayportService.getOperationLogsBetween(start, end);
            if (summary.events == null) {
                summary.events = new ArrayList<>();
            }
        } catch (Exception e) {
            summary.events = new ArrayList<>();
        }

        try {
            petRepository.findAll().stream()
                    .filter(p -> p != null && p.getCreatedAt() != null)
                    .filter(p -> !p.getCreatedAt().toLocalDate().isBefore(start)
                            && !p.getCreatedAt().toLocalDate().isAfter(end))
                    .forEach(p -> {
                        try {
                            ReportSummary.NewPatient patient = new ReportSummary.NewPatient();
                            patient.petName = p.getName() != null ? p.getName() : "";
                            patient.ownerName = p.getOwner() != null ? p.getOwner() : "";
                            patient.addedAt = p.getCreatedAt() != null ? p.getCreatedAt().toString() : "";
                            summary.newPatients.add(patient);
                        } catch (Exception e) {
                            // Skip this pet if there's an error
                        }
                    });
        } catch (Exception e) {
            // Continue with empty newPatients list
        }

        try {
            posSaleLineBackfillService.backfillMissingLines();
            LocalDateTime rangeStart = start.atStartOfDay();
            LocalDateTime rangeEndEx = end.plusDays(1).atStartOfDay();
            BigDecimal posSales = saleRepository.sumAmountBySourceBetween("POS", rangeStart, rangeEndEx);
            summary.posSales = MoneyUtils.normalize(posSales);
            summary.totalProfit = summary.posSales;

            List<SaleLine> procLines = saleLineRepository.findPosLinesByKindBetween(
                    SaleLine.KIND_PROCEDURE, rangeStart, rangeEndEx);
            List<SaleLine> prodLines = saleLineRepository.findPosLinesByKindBetween(
                    SaleLine.KIND_PRODUCT, rangeStart, rangeEndEx);

            for (SaleLine sl : procLines) {
                summary.posProcedureLines.add(mapPosLine(sl));
            }
            for (SaleLine sl : prodLines) {
                summary.posProductLines.add(mapPosLine(sl));
            }

            appendVirtualLinesFromPosSalesWithoutRows(rangeStart, rangeEndEx, summary);

            summary.posProcedureRevenue = MoneyUtils.normalize(summary.posProcedureLines.stream()
                    .map(r -> r.lineTotal)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            summary.posProductRevenue = MoneyUtils.normalize(summary.posProductLines.stream()
                    .map(r -> r.lineTotal)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        } catch (Exception e) {
            summary.totalProfit = BigDecimal.ZERO;
            summary.posSales = BigDecimal.ZERO;
        }

        return summary;
    }

    private static ReportSummary.PosSaleLineRow mapPosLine(SaleLine sl) {
        ReportSummary.PosSaleLineRow r = new ReportSummary.PosSaleLineRow();
        Sale sale = sl.getSale();
        r.date = sale.getOccurredAt() != null ? sale.getOccurredAt().toString() : "";
        r.saleId = sale.getId();
        r.petName = sale.getPetName() != null && !sale.getPetName().isBlank() ? sale.getPetName() : "—";
        r.itemName = sl.getItemName() != null ? sl.getItemName() : "";
        r.sku = sl.getSku() != null ? sl.getSku() : "";
        r.quantity = sl.getQuantity();
        r.unitPrice = sl.getUnitPrice();
        r.lineTotal = sl.getLineTotal();
        return r;
    }

    /**
     * When a POS {@link Sale} has no {@link SaleLine} rows (legacy DB, failed backfill, etc.),
     * still show item names on the report by parsing {@link Sale#getNote()}.
     */
    private void appendVirtualLinesFromPosSalesWithoutRows(LocalDateTime rangeStart,
                                                           LocalDateTime rangeEndEx,
                                                           ReportSummary summary) {
        List<Sale> sales = saleRepository.findPosSalesOccurredBetween(rangeStart, rangeEndEx);
        for (Sale sale : sales) {
            if (sale == null || sale.getId() == null) {
                continue;
            }
            if (saleLineRepository.countBySale_Id(sale.getId()) > 0) {
                continue;
            }
            List<PosSaleNoteParser.ParsedLine> parsed = PosSaleNoteParser.parse(sale.getNote());
            if (parsed.isEmpty()) {
                BigDecimal amt = sale.getAmount() != null ? MoneyUtils.normalize(sale.getAmount()) : BigDecimal.ZERO;
                if (amt.signum() > 0) {
                    summary.posProductLines.add(mapVirtualPosRow(sale, "POS sale", "", 1, amt, amt));
                }
                continue;
            }
            for (PosSaleNoteParser.ParsedLine p : parsed) {
                BigDecimal lineTotal = MoneyUtils.normalize(
                        p.unitPrice().multiply(BigDecimal.valueOf(p.quantity())));
                String sku = inventoryItemRepository.findFirstByNameIgnoreCase(p.itemName())
                        .map(InventoryItem::getSku)
                        .orElse("");
                ReportSummary.PosSaleLineRow row = mapVirtualPosRow(
                        sale, p.itemName(), sku, p.quantity(), p.unitPrice(), lineTotal);
                if (SaleLine.KIND_PROCEDURE.equals(resolvePosLineKind(p.itemName()))) {
                    summary.posProcedureLines.add(row);
                } else {
                    summary.posProductLines.add(row);
                }
            }
        }
    }

    private String resolvePosLineKind(String itemName) {
        return inventoryItemRepository.findFirstByNameIgnoreCase(itemName)
                .map(item -> {
                    String c = item.getCategory();
                    if (c != null && "SERVICE".equalsIgnoreCase(c.trim())) {
                        return SaleLine.KIND_PROCEDURE;
                    }
                    return SaleLine.KIND_PRODUCT;
                })
                .orElse(SaleLine.KIND_PRODUCT);
    }

    private static ReportSummary.PosSaleLineRow mapVirtualPosRow(Sale sale,
                                                                 String itemName,
                                                                 String sku,
                                                                 int quantity,
                                                                 BigDecimal unitPrice,
                                                                 BigDecimal lineTotal) {
        ReportSummary.PosSaleLineRow r = new ReportSummary.PosSaleLineRow();
        r.date = sale.getOccurredAt() != null ? sale.getOccurredAt().toString() : "";
        r.saleId = sale.getId();
        r.petName = sale.getPetName() != null && !sale.getPetName().isBlank() ? sale.getPetName() : "—";
        r.itemName = itemName != null ? itemName : "";
        r.sku = sku != null ? sku : "";
        r.quantity = quantity;
        r.unitPrice = unitPrice;
        r.lineTotal = lineTotal;
        return r;
    }
}


