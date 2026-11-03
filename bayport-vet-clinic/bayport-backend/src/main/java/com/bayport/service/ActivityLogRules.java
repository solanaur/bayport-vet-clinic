package com.bayport.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps existing audit / operation / data-access log fields to module categories
 * for Activity Log display. Does not rewrite stored records.
 */
public final class ActivityLogRules {

    public static final String ALL = "ALL";
    public static final String AUTHENTICATION = "AUTHENTICATION";
    public static final String USERS_ROLES = "USERS_ROLES";
    public static final String PETS = "PETS";
    public static final String APPOINTMENTS = "APPOINTMENTS";
    public static final String PRESCRIPTIONS = "PRESCRIPTIONS";
    public static final String BILLING = "BILLING";
    public static final String INVENTORY = "INVENTORY";
    public static final String BACKUPS = "BACKUPS";
    public static final String SECURITY = "SECURITY";
    public static final String SYSTEM = "SYSTEM";

    /** Stable display order for category tabs. */
    public static final String[] CATEGORY_ORDER = {
            ALL,
            AUTHENTICATION,
            USERS_ROLES,
            PETS,
            APPOINTMENTS,
            PRESCRIPTIONS,
            BILLING,
            INVENTORY,
            BACKUPS,
            SECURITY,
            SYSTEM
    };

    private ActivityLogRules() {}

    public static Map<String, String> categoryLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(ALL, "All Activities");
        labels.put(AUTHENTICATION, "Authentication");
        labels.put(USERS_ROLES, "Users & Roles");
        labels.put(PETS, "Pets");
        labels.put(APPOINTMENTS, "Appointments");
        labels.put(PRESCRIPTIONS, "Prescriptions");
        labels.put(BILLING, "Billing");
        labels.put(INVENTORY, "Inventory");
        labels.put(BACKUPS, "Backups");
        labels.put(SECURITY, "Security");
        labels.put(SYSTEM, "System");
        return labels;
    }

    public static String labelFor(String categoryKey) {
        return categoryLabels().getOrDefault(categoryKey, "System");
    }

    public static String categorizeAudit(String action, String entityName) {
        String a = norm(action);
        String e = norm(entityName);

        if (isAuthAction(a)) {
            return AUTHENTICATION;
        }
        if (isSecurityAction(a) || "AUTH".equals(e)) {
            return SECURITY;
        }
        if (a.startsWith("BACKUP") || "BACKUP".equals(e)) {
            return BACKUPS;
        }
        if (a.startsWith("PET_")) {
            return PETS;
        }
        if (a.startsWith("APPT_") || a.contains("APPOINTMENT")) {
            return APPOINTMENTS;
        }
        if (a.startsWith("PRESCRIPTION_") || a.startsWith("RX_")) {
            return PRESCRIPTIONS;
        }
        if (isBillingAction(a)) {
            return BILLING;
        }
        if (isInventoryAction(a)) {
            return INVENTORY;
        }
        if (isUserAction(a)) {
            return USERS_ROLES;
        }
        if (isSystemAction(a)) {
            return SYSTEM;
        }

        // Generic verbs (CREATE/UPDATE/DELETE/RESTORE/…) — use entity when reliable
        if (isPetEntity(e)) {
            return PETS;
        }
        if (isAppointmentEntity(e)) {
            return APPOINTMENTS;
        }
        if (isPrescriptionEntity(e)) {
            return PRESCRIPTIONS;
        }
        if (isBillingEntity(e)) {
            return BILLING;
        }
        if (isInventoryEntity(e)) {
            return INVENTORY;
        }
        if (isUserEntity(e)) {
            return USERS_ROLES;
        }
        if (isSystemEntity(e)) {
            return SYSTEM;
        }

        // No reliable module signal — do not guess
        return SYSTEM;
    }

    public static String categorizeOperation(String type) {
        String t = norm(type);
        if (t.contains("PET")) {
            return PETS;
        }
        if (t.contains("APPT") || t.contains("APPOINTMENT")) {
            return APPOINTMENTS;
        }
        if (t.contains("RX") || t.contains("PRESCRIPTION")) {
            return PRESCRIPTIONS;
        }
        if (t.contains("BILL") || t.contains("INVOICE") || t.contains("PAYMENT") || t.contains("SALE")) {
            return BILLING;
        }
        if (t.contains("INVENTORY") || t.contains("STOCK") || t.contains("PRODUCT")) {
            return INVENTORY;
        }
        if (t.contains("BACKUP")) {
            return BACKUPS;
        }
        if (t.contains("USER") || t.contains("ROLE")) {
            return USERS_ROLES;
        }
        if (t.contains("LOGIN") || t.contains("LOGOUT") || t.contains("AUTH")) {
            return AUTHENTICATION;
        }
        return SYSTEM;
    }

    public static String categorizeDataAccess(String entityName, String operation) {
        String e = norm(entityName);
        String op = norm(operation);
        if (op.contains("UNAUTHORIZED") || op.contains("DENIED") || op.contains("FORBIDDEN")) {
            return SECURITY;
        }
        if (isPetEntity(e)) {
            return PETS;
        }
        if (isAppointmentEntity(e)) {
            return APPOINTMENTS;
        }
        if (isPrescriptionEntity(e)) {
            return PRESCRIPTIONS;
        }
        if (isBillingEntity(e)) {
            return BILLING;
        }
        if (isInventoryEntity(e)) {
            return INVENTORY;
        }
        if (isUserEntity(e)) {
            return USERS_ROLES;
        }
        if ("BACKUP".equals(e)) {
            return BACKUPS;
        }
        return SYSTEM;
    }

    public static String displayMessage(String action, String details, String fallback) {
        if (details != null && !details.isBlank()) {
            return details.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        if (action == null || action.isBlank()) {
            return "Activity";
        }
        String spaced = action.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
        if (spaced.isEmpty()) {
            return "Activity";
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static boolean isAuthAction(String a) {
        return a.equals("LOGIN_SUCCESS")
                || a.equals("LOGIN_FAILED")
                || a.equals("LOGOUT")
                || a.equals("DEV_BYPASS_LOGIN")
                || a.equals("TEMP_ADMIN_LOGIN")
                || a.equals("LOGIN_RATE_LIMITED")
                || a.equals("LOGIN_LOCKED")
                || a.equals("PASSWORD_CHANGE_REQUIRED");
    }

    private static boolean isSecurityAction(String a) {
        return a.contains("MFA")
                || a.contains("TOTP")
                || a.contains("OTP")
                || a.equals("TOKEN_REJECTED")
                || a.contains("UNAUTHORIZED")
                || a.equals("PASSWORD_RESET_REQUESTED")
                || a.equals("PASSWORD_RESET_FAILED")
                || a.equals("PASSWORD_RESET");
    }

    private static boolean isUserAction(String a) {
        return a.startsWith("USER_")
                || a.equals("ROLE_CHANGED")
                || a.equals("PASSWORD_CHANGED")
                || a.equals("TOS_ACCEPTED");
    }

    private static boolean isBillingAction(String a) {
        return a.equals("VOID")
                || a.contains("INVOICE")
                || a.contains("PAYMENT")
                || a.contains("BILLING")
                || a.contains("CHECKOUT")
                || a.contains("SALE");
    }

    private static boolean isInventoryAction(String a) {
        return a.contains("INVENTORY") || a.contains("STOCK") || a.contains("PRODUCT");
    }

    private static boolean isSystemAction(String a) {
        return a.equals("IMPORT")
                || a.equals("EXPORT")
                || a.startsWith("SYSTEM_")
                || a.equals("SMS_ANNOUNCEMENT");
    }

    private static boolean isPetEntity(String e) {
        return e.equals("PET") || e.equals("OWNER");
    }

    private static boolean isAppointmentEntity(String e) {
        return e.equals("APPOINTMENT") || e.equals("CONSULTATION");
    }

    private static boolean isPrescriptionEntity(String e) {
        return e.equals("PRESCRIPTION") || e.equals("RX");
    }

    private static boolean isBillingEntity(String e) {
        return e.equals("INVOICE") || e.equals("BILLING") || e.equals("BILLINGRECORD")
                || e.equals("SALE") || e.equals("PAYMENT");
    }

    private static boolean isInventoryEntity(String e) {
        return e.equals("INVENTORY") || e.equals("INVENTORYITEM") || e.equals("PRODUCT") || e.equals("SERVICE");
    }

    private static boolean isUserEntity(String e) {
        return e.equals("USER") || e.equals("ROLE");
    }

    private static boolean isSystemEntity(String e) {
        return e.equals("SYSTEM") || e.equals("RECORD") || e.equals("EMAIL") || e.equals("ANNOUNCEMENT");
    }

    private static String norm(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
