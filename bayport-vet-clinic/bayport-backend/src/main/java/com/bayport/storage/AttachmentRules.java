package com.bayport.storage;

/**
 * Clinical attachments are limited to a small set of document types.
 */
public final class AttachmentRules {

    public static final long MAX_BYTES = 5L * 1024L * 1024L;

    private AttachmentRules() {}

    public static String problem(String filename, long size) {
        if (size <= 0 || size > MAX_BYTES) {
            return "File must be 5 MB or smaller.";
        }
        String lower = filename == null ? "" : filename.toLowerCase(java.util.Locale.ROOT);
        if (!(lower.endsWith(".pdf") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png"))) {
            return "Only PDF, JPG, and PNG files are accepted.";
        }
        return null;
    }
}
