package com.bayport.service;

import com.bayport.entity.InventoryItem;
import com.bayport.entity.InventorySkuSuppression;
import com.bayport.repository.InventoryItemRepository;
import com.bayport.repository.InventorySkuSuppressionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class InventoryService {

    private final InventoryItemRepository repository;
    private final InventorySkuSuppressionRepository skuSuppressionRepository;

    public InventoryService(InventoryItemRepository repository,
                              InventorySkuSuppressionRepository skuSuppressionRepository) {
        this.repository = repository;
        this.skuSuppressionRepository = skuSuppressionRepository;
    }

    public List<InventoryItem> list() {
        return repository.findAll();
    }

    public InventoryItem get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found"));
    }

    public Optional<InventoryItem> findBySku(String sku) {
        if (sku == null || sku.isBlank()) {
            return Optional.empty();
        }
        return repository.findBySku(sku.trim());
    }

    public InventoryItem create(InventoryItem item) {
        repository.findBySku(item.getSku()).ifPresent(existing -> {
            throw new IllegalArgumentException("SKU already exists");
        });
        normalizeStockFields(item);
        return repository.save(item);
    }

    public InventoryItem update(Long id, InventoryItem item) {
        InventoryItem existing = get(id);
        existing.setName(item.getName());
        existing.setSku(item.getSku());
        existing.setCategory(item.getCategory());
        existing.setUnit(item.getUnit());
        existing.setQuantity(item.getQuantity());
        existing.setReorderLevel(item.getReorderLevel());
        existing.setUnitPrice(item.getUnitPrice());
        existing.setPhoto(item.getPhoto());
        existing.setBatchNo(item.getBatchNo());
        existing.setExpiryDate(item.getExpiryDate());
        existing.setConsumableSku(item.getConsumableSku());
        normalizeStockFields(existing);
        return repository.save(existing);
    }

    public void delete(Long id) {
        InventoryItem item = get(id);
        String sku = item.getSku();
        if (sku != null && !sku.isBlank()) {
            String key = sku.trim().toUpperCase(Locale.ROOT);
            if (!skuSuppressionRepository.existsById(key)) {
                skuSuppressionRepository.save(new InventorySkuSuppression(key));
            }
        }
        repository.deleteById(id);
    }

    /**
     * Decrease stocked quantity for a sale. Services are non-stock; their linked
     * consumable product (if any) is handled separately via {@link #applySaleStock}.
     */
    public void decreaseQuantityForSale(Long id, int qtySold) {
        if (qtySold <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        InventoryItem item = get(id);
        if (InventoryRules.isService(item)) {
            return;
        }
        Integer current = item.getQuantity();
        if (current == null) {
            return;
        }
        if (current < qtySold) {
            throw new IllegalArgumentException("Insufficient stock for \"" + item.getName() + "\" (have " + current + ", need " + qtySold + ")");
        }
        item.setQuantity(current - qtySold);
        if (item.getVersion() == null) {
            item.setVersion(0L);
        }
        repository.save(item);
    }

    /**
     * Apply stock changes for one checkout/pending line.
     * Products deduct themselves. Services deduct a linked consumable product when configured.
     * {@code alreadyDeducted} prevents charging the same product stock twice in one sale.
     */
    public void applySaleStock(InventoryItem item, int qtySold, Set<Long> alreadyDeducted) {
        if (item == null || qtySold <= 0) {
            return;
        }
        if (InventoryRules.isService(item)) {
            String consumableSku = InventoryRules.consumableSkuFor(item);
            if (consumableSku == null || consumableSku.isBlank()) {
                return;
            }
            InventoryItem product = findBySku(consumableSku).orElse(null);
            if (product == null || InventoryRules.isService(product) || product.getId() == null) {
                return;
            }
            if (alreadyDeducted != null && alreadyDeducted.contains(product.getId())) {
                return;
            }
            decreaseQuantityForSale(product.getId(), qtySold);
            if (alreadyDeducted != null) {
                alreadyDeducted.add(product.getId());
            }
            return;
        }
        if (item.getId() != null && alreadyDeducted != null && alreadyDeducted.contains(item.getId())) {
            return;
        }
        decreaseQuantityForSale(item.getId(), qtySold);
        if (item.getId() != null && alreadyDeducted != null) {
            alreadyDeducted.add(item.getId());
        }
    }

    private static void normalizeStockFields(InventoryItem item) {
        if (InventoryRules.isService(item)) {
            item.setQuantity(null);
            item.setReorderLevel(null);
            if (item.getUnit() == null || item.getUnit().isBlank()) {
                item.setUnit("ea");
            }
        } else if (item.getQuantity() == null) {
            item.setQuantity(0);
        }
    }
}
