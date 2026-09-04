package com.bayport.web;

import com.bayport.entity.InventoryItem;
import com.bayport.repository.InventoryItemRepository;
import com.bayport.service.InventoryCatalogService;
import com.bayport.service.InventoryService;
import com.bayport.service.PdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryCatalogService inventoryCatalogService;
    private final InventoryItemRepository inventoryItemRepository;
    private final PdfService pdfService;

    public InventoryController(InventoryService inventoryService,
                               InventoryCatalogService inventoryCatalogService,
                               InventoryItemRepository inventoryItemRepository,
                               PdfService pdfService) {
        this.inventoryService = inventoryService;
        this.inventoryCatalogService = inventoryCatalogService;
        this.inventoryItemRepository = inventoryItemRepository;
        this.pdfService = pdfService;
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
     * Merges built-in product + procedure/service catalog rows (by SKU). Skips SKUs you removed from inventory
     * so they do not reappear after restart.
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

    @GetMapping(value = "/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','FRONT_OFFICE','RECEPTIONIST','PHARMACIST','VET','STAFF')")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(name = "type", defaultValue = "products") String type,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "preparedBy", required = false) String preparedBy
    ) {
        String tab = "services".equalsIgnoreCase(type) ? "services" : "products";
        String q = search != null ? search.trim().toLowerCase(Locale.ROOT) : "";
        List<InventoryItem> items = inventoryService.list().stream()
                .filter((item) -> tab.equals("services")
                        ? isServiceItem(item)
                        : !isServiceItem(item))
                .filter((item) -> matchesSearch(item, q))
                .sorted(Comparator.comparing((InventoryItem i) -> i.getName() != null ? i.getName() : "",
                        String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        byte[] pdf = pdfService.buildInventoryPdf(items, tab, search, preparedBy);
        String today = java.time.LocalDate.now().toString();
        String label = tab.equals("services") ? "Services" : "Products";
        String filename = String.format("Bayport_Inventory_%s_%s.pdf", label, today);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .body(pdf);
    }

    private static boolean isServiceItem(InventoryItem item) {
        return item.getCategory() != null && "SERVICE".equalsIgnoreCase(item.getCategory().trim());
    }

    private static boolean matchesSearch(InventoryItem item, String q) {
        if (q.isEmpty()) {
            return true;
        }
        return contains(item.getName(), q)
                || contains(item.getSku(), q)
                || contains(item.getCategory(), q);
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }
}

