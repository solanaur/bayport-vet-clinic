package com.bayport.service;

import com.bayport.entity.InventoryItem;
import com.bayport.entity.Sale;
import com.bayport.entity.SaleLine;
import com.bayport.repository.InventoryItemRepository;
import com.bayport.repository.SaleLineRepository;
import com.bayport.repository.SaleRepository;
import com.bayport.util.MoneyUtils;
import com.bayport.util.PosSaleNoteParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Older POS sales only had a {@link Sale} row; line-level reporting needs {@link SaleLine} rows.
 * This service rebuilds lines from the human-readable {@link Sale#getNote()} when a sale has no lines.
 */
@Service
public class PosSaleLineBackfillService {

    private static final Logger log = LoggerFactory.getLogger(PosSaleLineBackfillService.class);
    private final SaleRepository saleRepository;
    private final SaleLineRepository saleLineRepository;
    private final InventoryItemRepository inventoryItemRepository;

    public PosSaleLineBackfillService(SaleRepository saleRepository,
                                      SaleLineRepository saleLineRepository,
                                      InventoryItemRepository inventoryItemRepository) {
        this.saleRepository = saleRepository;
        this.saleLineRepository = saleLineRepository;
        this.inventoryItemRepository = inventoryItemRepository;
    }

    /**
     * @return number of {@link Sale} records that received new lines
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int backfillMissingLines() {
        List<Sale> missing = saleRepository.findPosSalesWithNoLines();
        int done = 0;
        for (Sale sale : missing) {
            if (sale == null || sale.getId() == null) {
                continue;
            }
            if (sale.getAmount() == null || sale.getAmount().signum() <= 0) {
                continue;
            }
            try {
                if (backfillOne(sale)) {
                    done++;
                }
            } catch (Exception e) {
                log.warn("Could not backfill sale lines for sale id={}: {}", sale.getId(), e.getMessage());
            }
        }
        if (done > 0) {
            log.info("Backfilled {} POS sale(s) with sale_lines from stored notes", done);
        }
        return done;
    }

    private boolean backfillOne(Sale sale) {
        if (saleLineRepository.countBySale_Id(sale.getId()) > 0) {
            return false;
        }

        List<PosSaleNoteParser.ParsedLine> parsed = PosSaleNoteParser.parse(sale.getNote());

        List<SaleLine> lines = new ArrayList<>();
        if (!parsed.isEmpty()) {
            for (PosSaleNoteParser.ParsedLine p : parsed) {
                BigDecimal lineTotal = MoneyUtils.normalize(p.unitPrice().multiply(BigDecimal.valueOf(p.quantity())));
                SaleLine sl = new SaleLine();
                sl.setSale(sale);
                sl.setLineKind(resolveLineKind(p.itemName()));
                sl.setItemName(p.itemName());
                sl.setSku(lookupSku(p.itemName()));
                sl.setQuantity(p.quantity());
                sl.setUnitPrice(p.unitPrice());
                sl.setLineTotal(lineTotal);
                lines.add(sl);
            }
        }

        if (lines.isEmpty()) {
            BigDecimal amt = MoneyUtils.normalize(sale.getAmount());
            SaleLine sl = new SaleLine();
            sl.setSale(sale);
            sl.setLineKind(SaleLine.KIND_PRODUCT);
            sl.setItemName("POS sale");
            sl.setSku(null);
            sl.setQuantity(1);
            sl.setUnitPrice(amt);
            sl.setLineTotal(amt);
            lines.add(sl);
        }

        saleLineRepository.saveAll(lines);
        return true;
    }

    private String resolveLineKind(String itemName) {
        return inventoryItemRepository.findFirstByNameIgnoreCase(itemName)
                .map(this::lineKindForItem)
                .orElse(SaleLine.KIND_PRODUCT);
    }

    private String lineKindForItem(InventoryItem item) {
        String c = item.getCategory();
        if (c != null && "SERVICE".equalsIgnoreCase(c.trim())) {
            return SaleLine.KIND_PROCEDURE;
        }
        return SaleLine.KIND_PRODUCT;
    }

    private String lookupSku(String itemName) {
        return inventoryItemRepository.findFirstByNameIgnoreCase(itemName)
                .map(InventoryItem::getSku)
                .orElse(null);
    }
}
