package com.bayport.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemainingClinicRulesTest {

    @Test
    void anotherAppointmentInTheSameSlotIsAConflict() {
        assertTrue(AppointmentSlots.takenBySomeoneElse(5L, List.of(5L, 9L)));
        assertFalse(AppointmentSlots.takenBySomeoneElse(5L, List.of(5L)));
        assertTrue(AppointmentSlots.takenBySomeoneElse(null, List.of(9L)));
    }

    @Test
    void importPreviewReportsDuplicatesAndMissingFields() {
        List<Map<String, String>> errors = ClinicImportRules.check(
                "owners",
                List.of(
                        Map.of("fullName", "Ana Cruz", "phone", "09170000001"),
                        Map.of("fullName", "", "phone", "09170000002"),
                        Map.of("fullName", "Second", "phone", "09170000001")
                ),
                Set.of());
        assertEquals(2, errors.size());
    }

    @Test
    void deceasedAndInactivePetsAreNotReminded() {
        assertFalse(ReminderDispatchPolicy.allowForPet("DECEASED"));
        assertFalse(ReminderDispatchPolicy.allowForPet("inactive"));
        assertTrue(ReminderDispatchPolicy.allowForPet("ACTIVE"));
        assertTrue(ReminderDispatchPolicy.allowForPet("CAUTION"));
    }

    @Test
    void optedOutOwnersAreNotReminded() {
        assertFalse(ReminderDispatchPolicy.allowForOwner(true));
        assertTrue(ReminderDispatchPolicy.allowForOwner(false));
    }

    @Test
    void ownerImportRequiresAnElevenDigitPhone() {
        List<Map<String, String>> errors = ClinicImportRules.check(
                "owners",
                List.of(Map.of("fullName", "Ana Cruz", "phone", "123")),
                Set.of());
        assertEquals(1, errors.size());
        assertEquals("phone must be 11 digits", errors.get(0).get("message"));
    }

    @Test
    void voidNeedsAReasonAndCannotBeRepeated() {
        assertEquals("A reason is required.", BillingVoidRules.problem("PAID", "no"));
        assertEquals(null, BillingVoidRules.problem("PENDING", "Owner cancelled the visit"));
        assertEquals("This invoice is already void.", BillingVoidRules.problem("VOID", "Owner cancelled the visit"));
    }

    @Test
    void attachmentsMustBeSmallPdfOrImageFiles() {
        assertEquals(null, com.bayport.storage.AttachmentRules.problem("vaccine.pdf", 1024));
        assertEquals("Only PDF, JPG, and PNG files are accepted.",
                com.bayport.storage.AttachmentRules.problem("notes.exe", 1024));
        assertEquals("File must be 5 MB or smaller.",
                com.bayport.storage.AttachmentRules.problem("scan.jpg", com.bayport.storage.AttachmentRules.MAX_BYTES + 1));
    }
}
