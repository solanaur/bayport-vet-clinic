package com.bayport.service;

import com.bayport.dto.ReportNewPatient;
import com.bayport.dto.ReportSummary;
import com.bayport.entity.BillingLine;
import com.bayport.entity.InventoryItem;
import com.bayport.entity.Sale;
import com.bayport.entity.SaleLine;
import com.bayport.dto.ReportDailyPosRow;
import com.bayport.dto.ReportTopItemRow;
import com.bayport.entity.BillingRecord;
import com.bayport.repository.BillingLineRepository;
import com.bayport.repository.BillingRecordRepository;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

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
    private final BillingRecordRepository billingRecordRepository;
    private final BillingLineRepository billingLineRepository;

    public ReportService(PetRepository petRepository,
                         BayportService bayportService,
                         PrescriptionRepository prescriptionRepository,
                         SaleRepository saleRepository,
                         SaleLineRepository saleLineRepository,
                         PosSaleLineBackfillService posSaleLineBackfillService,
                         InventoryItemRepository inventoryItemRepository,
                         BillingRecordRepository billingRecordRepository,
                         BillingLineRepository billingLineRepository) {
        this.petRepository = petRepository;
        this.bayportService = bayportService;
        this.prescriptionRepository = prescriptionRepository;
        this.saleRepository = saleRepository;
        this.saleLineRepository = saleLineRepository;
        this.posSaleLineBackfillService = posSaleLineBackfillService;
        this.inventoryItemRepository = inventoryItemRepository;
        this.billingRecordRepository = billingRecordRepository;
        this.billingLineRepository = billingLineRepository;
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
                            ReportNewPatient patient = new ReportNewPatient();
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
            java.util.Set<Long> voidedBillingIds = voidedBillingIds();
            BigDecimal posSales = BigDecimal.ZERO;
            for (Sale s : saleRepository.findPosSalesOccurredBetween(rangeStart, rangeEndEx)) {
                if (s == null || isSaleLinkedToVoidedInvoice(s, voidedBillingIds)) {
                    continue;
                }
                if (s.getAmount() != null) {
                    posSales = posSales.add(s.getAmount());
                }
            }
            summary.posSales = MoneyUtils.normalize(posSales);
            summary.totalProfit = summary.posSales;

            List<SaleLine> procLines = saleLineRepository.findPosLinesByKindBetween(
                    SaleLine.KIND_PROCEDURE, rangeStart, rangeEndEx);
            List<SaleLine> prodLines = saleLineRepository.findPosLinesByKindBetween(
                    SaleLine.KIND_PRODUCT, rangeStart, rangeEndEx);

            for (SaleLine sl : procLines) {
                if (sl != null && sl.getSale() != null
                        && isSaleLinkedToVoidedInvoice(sl.getSale(), voidedBillingIds)) {
                    continue;
                }
                summary.posProcedureLines.add(mapPosLine(sl));
            }
            for (SaleLine sl : prodLines) {
                if (sl != null && sl.getSale() != null
                        && isSaleLinkedToVoidedInvoice(sl.getSale(), voidedBillingIds)) {
                    continue;
                }
                summary.posProductLines.add(mapPosLine(sl));
            }

            appendVirtualLinesFromPosSalesWithoutRows(rangeStart, rangeEndEx, summary, voidedBillingIds);

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

        try {
            LocalDateTime rangeStart = start.atStartOfDay();
            LocalDateTime rangeEndEx = end.plusDays(1).atStartOfDay();
            for (BillingLine line : billingLineRepository.findIssuedBetween(rangeStart, rangeEndEx)) {
                summary.consultationServiceLines.add(mapConsultationLine(line));
            }
            summary.consultationServiceRevenue = MoneyUtils.normalize(
                    summary.consultationServiceLines.stream()
                            .map(r -> r.serviceCost)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
        } catch (Exception e) {
            summary.consultationServiceLines = new ArrayList<>();
            summary.consultationServiceRevenue = BigDecimal.ZERO;
        }

        enrichFinancialAndDashboard(start, end, summary);
        return summary;
    }

    /**
     * POS payment mix, pending billing, daily totals, and top items — aligned with the Reports screen.
     */
    private void enrichFinancialAndDashboard(LocalDate start, LocalDate end, ReportSummary summary) {
        try {
            LocalDateTime rangeStart = start.atStartOfDay();
            LocalDateTime rangeEndEx = end.plusDays(1).atStartOfDay();

            BigDecimal pending = billingRecordRepository.findAll().stream()
                    .filter(b -> b != null && b.getStatus() == BillingRecord.Status.PENDING)
                    .filter(b -> b.getIssuedAt() != null
                            && !b.getIssuedAt().isBefore(rangeStart)
                            && b.getIssuedAt().isBefore(rangeEndEx))
                    .map(BillingRecord::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            summary.pendingBilling = MoneyUtils.normalize(pending);
            BigDecimal voided = billingRecordRepository.findAll().stream()
                    .filter(b -> b != null && b.getStatus() == BillingRecord.Status.VOID)
                    .filter(b -> {
                        LocalDateTime when = b.getVoidedAt() != null ? b.getVoidedAt() : b.getIssuedAt();
                        return when != null
                                && !when.isBefore(rangeStart)
                                && when.isBefore(rangeEndEx);
                    })
                    .map(BillingRecord::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            summary.voidedAmount = MoneyUtils.normalize(voided);

            BigDecimal cash = BigDecimal.ZERO;
            BigDecimal card = BigDecimal.ZERO;
            BigDecimal other = BigDecimal.ZERO;
            Map<LocalDate, BigDecimal> dayTotals = new TreeMap<>();
            Map<LocalDate, Integer> dayCounts = new TreeMap<>();

            Set<Long> voidedBillingIds = voidedBillingIds();
            List<Sale> posSales = saleRepository.findPosSalesOccurredBetween(rangeStart, rangeEndEx);
            for (Sale s : posSales) {
                if (s == null || isSaleLinkedToVoidedInvoice(s, voidedBillingIds)) {
                    continue;
                }
                BigDecimal amt = s.getAmount() != null ? MoneyUtils.normalize(s.getAmount()) : BigDecimal.ZERO;
                switch (PosSaleNoteParser.paymentBucket(s.getNote())) {
                    case CASH -> cash = cash.add(amt);
                    case CARD -> card = card.add(amt);
                    default -> other = other.add(amt);
                }
                if (s.getOccurredAt() != null) {
                    LocalDate d = s.getOccurredAt().toLocalDate();
                    dayTotals.merge(d, amt, BigDecimal::add);
                    dayCounts.merge(d, 1, Integer::sum);
                }
            }
            summary.posCash = MoneyUtils.normalize(cash);
            summary.posCard = MoneyUtils.normalize(card);
            summary.posOtherPayment = MoneyUtils.normalize(other);

            for (Map.Entry<LocalDate, BigDecimal> e : dayTotals.entrySet()) {
                ReportDailyPosRow row = new ReportDailyPosRow();
                row.date = e.getKey().toString();
                row.transactions = dayCounts.getOrDefault(e.getKey(), 0);
                row.total = MoneyUtils.normalize(e.getValue());
                summary.dailyPos.add(row);
            }

            computeTopItems(summary);
        } catch (Exception ignored) {
            // Leave defaults; core summary still useful
        }
    }

    private void computeTopItems(ReportSummary summary) {
        record Agg(String name, String cat, int qty, BigDecimal total) {}
        Map<String, Agg> map = new HashMap<>();
        java.util.function.BiConsumer<ReportSummary.PosSaleLineRow, String> add = (row, kind) -> {
            String name = row.itemName != null && !row.itemName.isBlank() ? row.itemName.trim() : "Item";
            int q = row.quantity;
            BigDecimal lt = row.lineTotal != null ? row.lineTotal : BigDecimal.ZERO;
            Agg prev = map.get(name);
            String cat = prev != null && prev.cat != null && !prev.cat.isBlank()
                    ? prev.cat
                    : inferItemCategory(name, kind);
            if (prev == null) {
                map.put(name, new Agg(name, cat, q, lt));
            } else {
                map.put(name, new Agg(name, cat, prev.qty + q, prev.total.add(lt)));
            }
        };
        for (ReportSummary.PosSaleLineRow r : summary.posProcedureLines) {
            add.accept(r, "procedure");
        }
        for (ReportSummary.PosSaleLineRow r : summary.posProductLines) {
            add.accept(r, "product");
        }
        summary.topItems = map.values().stream()
                .sorted(Comparator.comparing(Agg::total).reversed())
                .limit(40)
                .map(a -> {
                    ReportTopItemRow x = new ReportTopItemRow();
                    x.name = a.name;
                    x.category = a.cat;
                    x.qty = a.qty;
                    x.total = MoneyUtils.normalize(a.total);
                    return x;
                })
                .toList();
    }

    private static String inferItemCategory(String itemName, String kind) {
        if ("procedure".equals(kind)) {
            return "Service";
        }
        String n = itemName.toLowerCase();
        if (java.util.regex.Pattern.compile(
                "consult|visit|exam|service|fee|lab|x-ray|ultrasound|dental|surgery|therapy|test").matcher(n).find()) {
            return "Service";
        }
        if (n.contains("vacc") || n.contains("dhpp") || n.contains("rabies")) {
            return "Vaccine";
        }
        if (n.contains("tick") || n.contains("flea") || n.contains("worm")
                || n.contains("tablet") || n.contains("capsule") || n.contains("antibiotic")) {
            return "Medication";
        }
        return "Product";
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
    private Set<Long> voidedBillingIds() {
        try {
            return billingRecordRepository.findAll().stream()
                    .filter(b -> b != null && b.getStatus() == BillingRecord.Status.VOID && b.getId() != null)
                    .map(BillingRecord::getId)
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (Exception e) {
            return Set.of();
        }
    }

    private static boolean isSaleLinkedToVoidedInvoice(Sale sale, Set<Long> voidedBillingIds) {
        if (sale == null || voidedBillingIds == null || voidedBillingIds.isEmpty()) {
            return false;
        }
        Long billingId = sale.getBillingId();
        return billingId != null && voidedBillingIds.contains(billingId);
    }

    private void appendVirtualLinesFromPosSalesWithoutRows(LocalDateTime rangeStart,
                                                           LocalDateTime rangeEndEx,
                                                           ReportSummary summary,
                                                           Set<Long> voidedBillingIds) {
        List<Sale> sales = saleRepository.findPosSalesOccurredBetween(rangeStart, rangeEndEx);
        for (Sale sale : sales) {
            if (sale == null || sale.getId() == null) {
                continue;
            }
            if (isSaleLinkedToVoidedInvoice(sale, voidedBillingIds)) {
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
                .map(item -> InventoryRules.isService(item) ? SaleLine.KIND_PROCEDURE : SaleLine.KIND_PRODUCT)
                .orElse(SaleLine.KIND_PRODUCT);
    }

    private static ReportSummary.ConsultationServiceLine mapConsultationLine(BillingLine line) {
        ReportSummary.ConsultationServiceLine row = new ReportSummary.ConsultationServiceLine();
        BillingRecord invoice = line.getBilling();
        row.invoiceId = invoice != null ? invoice.getId() : null;
        row.consultationId = line.getConsultationId();
        row.issuedAt = invoice != null && invoice.getIssuedAt() != null ? invoice.getIssuedAt().toString() : "";
        row.petName = invoice != null && invoice.getPetName() != null ? invoice.getPetName() : "";
        row.ownerName = invoice != null && invoice.getOwnerName() != null ? invoice.getOwnerName() : "";
        row.serviceName = line.getServiceName() != null ? line.getServiceName() : "Service";
        row.performedBy = line.getPerformedBy() != null ? line.getPerformedBy() : "";
        row.serviceCost = MoneyUtils.normalize(line.getServiceCost());
        row.invoiceTotal = invoice != null ? MoneyUtils.normalize(invoice.getAmount()) : row.serviceCost;
        return row;
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


