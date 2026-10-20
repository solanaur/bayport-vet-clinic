package com.bayport.service;

/**
 * Decides whether a reminder can go out by email, SMS, or not at all.
 */
public final class ReminderDispatchPolicy {

    public record Plan(boolean hasContact, boolean sendEmail, boolean sendSms) {}

    private ReminderDispatchPolicy() {}

    public static Plan plan(String email, String phone, boolean mailConfigured, boolean smsConfigured) {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPhone = phone != null && !phone.isBlank();
        return new Plan(hasEmail || hasPhone, hasEmail && mailConfigured, hasPhone && smsConfigured);
    }

    public static boolean allowForPet(String recordStatus) {
        if (recordStatus == null || recordStatus.isBlank()) {
            return true;
        }
        String status = recordStatus.trim().toUpperCase(java.util.Locale.ROOT);
        return !"DECEASED".equals(status) && !"INACTIVE".equals(status);
    }

    public static boolean allowForOwner(boolean optedOut) {
        return !optedOut;
    }
}
