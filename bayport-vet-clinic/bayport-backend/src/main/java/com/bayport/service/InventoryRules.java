package com.bayport.service;

import com.bayport.entity.InventoryItem;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared inventory classification helpers. Services are non-stock catalog fees;
 * some services consume a linked product SKU when performed.
 */
public final class InventoryRules {

    public static final Set<String> SERVICE_CATEGORIES = Set.of(
            "SERVICE",
            "CONSULTATION",
            "VACCINATION",
            "LABORATORY TESTS",
            "GROOMING",
            "SURGERY",
            "TREATMENT",
            "DEWORMING",
            "RAPID TESTS",
            "SPAYING & CASTRATION",
            "SURGICAL SERVICE",
            "VACCINE & DEWORMING",
            "CONSULTATION & CHECK-UP"
    );

    /**
     * When a clinic service is charged, also deduct this product SKU once
     * (unless the same product was already deducted on the same sale).
     */
    private static final Map<String, String> DEFAULT_CONSUMABLE_BY_SERVICE_SKU = Map.ofEntries(
            Map.entry("SVC-006", "VACCINES-004"), // Feline 4 in 1
            Map.entry("SVC-007", "VACCINES-001"), // Canine 5 in 1
            Map.entry("SVC-008", "VACCINES-002"), // Canine 6 in 1
            Map.entry("SVC-009", "VACCINES-003"), // Canine 8 in 1
            Map.entry("SVC-010", "VACCINES-006"), // Kennel Cough
            Map.entry("SVC-011", "VACCINES-005")  // Anti-Rabies
    );

    private InventoryRules() {}

    public static boolean isService(InventoryItem item) {
        if (item == null) {
            return false;
        }
        return isService(item.getSku(), item.getCategory());
    }

    public static boolean isService(String sku, String category) {
        if (sku != null && sku.trim().toUpperCase(Locale.ROOT).startsWith("SVC-")) {
            return true;
        }
        if (category == null || category.isBlank()) {
            return false;
        }
        return SERVICE_CATEGORIES.contains(category.trim().toUpperCase(Locale.ROOT));
    }

    public static String consumableSkuFor(InventoryItem item) {
        if (item == null || !isService(item)) {
            return null;
        }
        String explicit = item.getConsumableSku();
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }
        String sku = item.getSku();
        if (sku == null || sku.isBlank()) {
            return null;
        }
        return DEFAULT_CONSUMABLE_BY_SERVICE_SKU.get(sku.trim().toUpperCase(Locale.ROOT));
    }

    /** Preferred display categories for new/edited services. */
    public static String[] preferredServiceCategories() {
        return new String[] {
                "Consultation",
                "Vaccination",
                "Laboratory Tests",
                "Grooming",
                "Surgery",
                "Treatment",
                "Deworming"
        };
    }
}
