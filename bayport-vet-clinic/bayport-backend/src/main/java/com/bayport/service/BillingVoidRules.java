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
        if (!"PAID".equals(current) && !"PENDING".equals(current)) {
            return "This invoice cannot be voided.";
        }
        return null;
    }
}
