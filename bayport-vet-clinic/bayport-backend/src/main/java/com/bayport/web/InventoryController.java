package com.bayport.web;

import com.bayport.entity.InventoryItem;
import com.bayport.repository.InventoryItemRepository;
import com.bayport.service.InventoryCatalogService;
import com.bayport.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryCatalogService inventoryCatalogService;
    private final InventoryItemRepository inventoryItemRepository;

    public InventoryController(InventoryService inventoryService,
                               InventoryCatalogService inventoryCatalogService,
                               InventoryItemRepository inventoryItemRepository) {
        this.inventoryService = inventoryService;
        this.inventoryCatalogService = inventoryCatalogService;
        this.inventoryItemRepository = inventoryItemRepository;
    }

    @GetMapping
    public List<InventoryItem> list() {
        return inventoryService.list();
    }

    @GetMapping("/{id}")
    public InventoryItem get(@PathVariable Long id) {
        return inventoryService.get(id);
    }

    @PostMapping
    public InventoryItem create(@RequestBody InventoryItem item) {
        return inventoryService.create(item);
    }

    @PutMapping("/{id}")
    public InventoryItem update(@PathVariable Long id, @RequestBody InventoryItem item) {
        return inventoryService.update(id, item);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        inventoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Merges built-in product + procedure/service catalog rows (by SKU). Safe to run anytime.
     */
    @PostMapping("/merge-catalog")
    @PreAuthorize("hasAnyRole('ADMIN','FRONT_OFFICE','RECEPTIONIST','PHARMACIST','VET','STAFF')")
    public ResponseEntity<Map<String, Object>> mergeCatalog() {
        long before = inventoryItemRepository.count();
        inventoryCatalogService.importMissingSkus();
        long after = inventoryItemRepository.count();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("rowsBefore", before);
        body.put("rowsAfter", after);
        body.put("rowsAddedThisRun", after - before);
        return ResponseEntity.ok(body);
    }
}

