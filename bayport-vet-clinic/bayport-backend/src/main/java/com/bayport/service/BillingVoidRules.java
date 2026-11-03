package com.bayport.service;

/**
 * An invoice can be voided once, and only with a written reason.
 */
public final class BillingVoidRules {

    private BillingVoidRules() {}

    public static String problem(String status, String reason) {
        if (reason == null || reason.trim().length() < 3) {
            return "A reason is required.";
        }
        String current = status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT);
        if ("VOID".equals(current)) {
            return "This invoice is already void.";
        }
        // Only unpaid invoices may be voided — paid revenue stays tied to the POS sale.
        if (!"PENDING".equals(current)) {
            return "Only pending invoices can be voided.";
        }
        return null;
    }
}
