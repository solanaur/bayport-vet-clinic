package com.bayport.config;

import com.bayport.entity.InventoryItem;
import com.bayport.repository.InventoryItemRepository;
import com.bayport.repository.InventorySkuSuppressionRepository;
import com.bayport.service.InventoryRules;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Merges catalog JSON (products + procedures/services) into {@code inventory_items} by SKU.
 * SKUs the user deleted are recorded in {@code inventory_sku_suppression} and are never re-inserted here.
 * Per-row failures are logged and skipped so one bad row does not block the rest.
 */
@Component
public class InventoryCatalogImporter {

    private static final Logger log = LoggerFactory.getLogger(InventoryCatalogImporter.class);

    private final InventoryItemRepository inventoryItemRepository;
    private final InventorySkuSuppressionRepository skuSuppressionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InventoryCatalogImporter(InventoryItemRepository inventoryItemRepository,
                                    InventorySkuSuppressionRepository skuSuppressionRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.skuSuppressionRepository = skuSuppressionRepository;
    }

    public void importMissingSkus() {
        int p = importResource("inventory-products.json", false);
        int s = importResource("inventory-services.json", true);
        log.info("Inventory catalog merge finished (products file: +{} rows, services file: +{} rows)", p, s);
    }

    /**
     * @return number of rows newly inserted from this file
     */
    private int importResource(String classpath, boolean servicesFile) {
        ClassPathResource resource = new ClassPathResource(classpath);
        if (!resource.exists()) {
            log.warn("Inventory catalog resource missing on classpath: {}", classpath);
            return 0;
        }
        int added = 0;
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            if (!root.isArray()) {
                log.warn("Inventory catalog {} is not a JSON array — skipped", classpath);
                return 0;
            }
            for (JsonNode n : root) {
                try {
                    if (importOneRow(n, servicesFile)) {
                        added++;
                    }
                } catch (Exception rowEx) {
                    log.error("Skipping bad catalog row in {}: {}", classpath, rowEx.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to read inventory catalog {}: {}", classpath, e.getMessage(), e);
        }
        return added;
    }

    /**
     * Ensures a catalog SKU exists in inventory (POS may send SKU-only lines for procedures).
     * Loads the row from classpath JSON and inserts if missing.
     */
    public InventoryItem ensureSkuInInventory(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU is required");
        }
        String trimmed = sku.trim();
        if (skuSuppressionRepository.existsById(trimmed.toUpperCase())) {
            throw new IllegalArgumentException(
                    "This item was removed from inventory and is not restored automatically: " + trimmed);
        }
        Optional<InventoryItem> existing = inventoryItemRepository.findBySku(trimmed);
        if (existing.isPresent()) {
            return existing.get();
        }
        InventoryItem built = loadItemFromCatalogBySku(trimmed);
        if (built == null) {
            throw new IllegalArgumentException("Unknown catalog SKU: " + trimmed);
        }
        return inventoryItemRepository.save(built);
    }

    private InventoryItem loadItemFromCatalogBySku(String sku) {
        for (String resource : new String[] { "inventory-products.json", "inventory-services.json" }) {
            ClassPathResource cpr = new ClassPathResource(resource);
            if (!cpr.exists()) {
                continue;
            }
            try (InputStream in = cpr.getInputStream()) {
                JsonNode root = objectMapper.readTree(in);
                if (!root.isArray()) {
                    continue;
                }
                for (JsonNode n : root) {
                    String rowSku = text(n, "sku");
                    if (sku.equalsIgnoreCase(rowSku)) {
                        return buildItemFromNode(n);
                    }
                }
            } catch (Exception e) {
                log.error("Error scanning {} for SKU {}: {}", resource, sku, e.getMessage());
            }
        }
        return null;
    }

    private boolean importOneRow(JsonNode n, boolean servicesFile) {
        String sku = text(n, "sku");
        if (sku == null || sku.isBlank()) {
            return false;
        }
        if (skuSuppressionRepository.existsById(sku.trim().toUpperCase())) {
            return false;
        }
        Optional<InventoryItem> existingOpt = inventoryItemRepository.findBySku(sku);
        if (existingOpt.isPresent()) {
            if (servicesFile) {
                refreshServiceMetadata(existingOpt.get(), n);
            }
            return false;
        }
        InventoryItem item = buildItemFromNode(n);
        if (item == null) {
            return false;
        }
        inventoryItemRepository.save(item);
        return true;
    }

    /**
     * Existing service rows keep prices/stock as edited; only refresh category + consumable link
     * from the catalog so meaningful categories replace legacy {@code SERVICE}.
     */
    private void refreshServiceMetadata(InventoryItem existing, JsonNode n) {
        if (!InventoryRules.isService(existing) && !InventoryRules.isService(text(n, "sku"), text(n, "category"))) {
            return;
        }
        boolean dirty = false;
        String category = text(n, "category");
        if (category != null && !category.isBlank()
                && (existing.getCategory() == null
                || "SERVICE".equalsIgnoreCase(existing.getCategory().trim())
                || !category.equalsIgnoreCase(existing.getCategory().trim()))) {
            existing.setCategory(category);
            dirty = true;
        }
        String consumable = text(n, "consumableSku");
        if (consumable != null && !consumable.isBlank()
                && (existing.getConsumableSku() == null || !consumable.equalsIgnoreCase(existing.getConsumableSku()))) {
            existing.setConsumableSku(consumable);
            dirty = true;
        }
        if (InventoryRules.isService(existing)) {
            if (existing.getQuantity() != null || existing.getReorderLevel() != null) {
                existing.setQuantity(null);
                existing.setReorderLevel(null);
                dirty = true;
            }
        }
        if (dirty) {
            inventoryItemRepository.save(existing);
        }
    }

    private InventoryItem buildItemFromNode(JsonNode n) {
        String sku = text(n, "sku");
        if (sku == null || sku.isBlank()) {
            return null;
        }
        InventoryItem item = new InventoryItem();
        item.setSku(sku);
        item.setName(text(n, "name"));
        item.setCategory(text(n, "category"));
        item.setUnit(text(n, "unit"));
        item.setConsumableSku(text(n, "consumableSku"));
        if (n.hasNonNull("unitPrice")) {
            item.setUnitPrice(BigDecimal.valueOf(n.get("unitPrice").asDouble()));
        }
        if (InventoryRules.isService(item)) {
            item.setQuantity(null);
            item.setReorderLevel(null);
            if (item.getUnit() == null || item.getUnit().isBlank()) {
                item.setUnit("ea");
            }
        } else {
            if (n.hasNonNull("quantity")) {
                item.setQuantity(n.get("quantity").asInt());
            } else {
                item.setQuantity(100);
            }
            item.setReorderLevel(5);
        }
        return item;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
