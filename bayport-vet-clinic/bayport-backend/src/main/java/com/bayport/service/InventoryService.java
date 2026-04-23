package com.bayport.service;

import com.bayport.entity.InventoryItem;
import com.bayport.repository.InventoryItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class InventoryService {

    private final InventoryItemRepository repository;

    public InventoryService(InventoryItemRepository repository) {
        this.repository = repository;
    }

    public List<InventoryItem> list() {
        return repository.findAll();
    }

    public InventoryItem get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found"));
    }

    public InventoryItem create(InventoryItem item) {
        repository.findBySku(item.getSku()).ifPresent(existing -> {
            throw new IllegalArgumentException("SKU already exists");
        });
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
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    /**
     * Decrease stocked quantity for a sale. Services ({@code category=SERVICE}) and items with
     * {@code quantity == null} are treated as non-stock (no decrement).
     */
    public void decreaseQuantityForSale(Long id, int qtySold) {
        if (qtySold <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        InventoryItem item = get(id);
        if (isNonStockItem(item)) {
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
        repository.save(item);
    }

    private static boolean isNonStockItem(InventoryItem item) {
        String cat = item.getCategory();
        return cat != null && "SERVICE".equalsIgnoreCase(cat.trim());
    }
}

