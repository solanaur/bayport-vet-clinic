package com.bayport.service;

/**
 * Blocks permanent deletion when a clinical or billing record still depends on the row.
 */
public final class DeletionGuard {

    private DeletionGuard() {}

    public static void requireNoInvoiceOrPrescription(long prescriptionCount, long invoiceCount, String recordLabel) {
        if (prescriptionCount > 0 || invoiceCount > 0) {
            throw new IllegalStateException(
                    "Cannot permanently delete this " + recordLabel
                            + " because a prescription or invoice still refers to it. Restore it from the recycle bin instead.");
        }
    }
}
