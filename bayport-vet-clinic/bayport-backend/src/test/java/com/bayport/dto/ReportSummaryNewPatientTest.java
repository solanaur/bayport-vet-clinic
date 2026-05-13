package com.bayport.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards against regressions to {@code ReportSummary$NewPatient} runtime failures:
 * {@code newPatients} must use the top-level {@link ReportNewPatient} type.
 */
class ReportSummaryNewPatientTest {

    @Test
    void newPatientsListAcceptsReportNewPatient() {
        ReportSummary summary = new ReportSummary();
        ReportNewPatient row = new ReportNewPatient();
        row.petName = "Mochi";
        row.ownerName = "K. Santos";
        row.addedAt = "2026-05-01T10:00:00";
        summary.newPatients.add(row);
        assertEquals(1, summary.newPatients.size());
        assertEquals("Mochi", summary.newPatients.get(0).petName);
    }

    @Test
    void jacksonSerializesNewPatientsAsExpected() throws Exception {
        ReportSummary summary = new ReportSummary();
        summary.period = "month";
        summary.from = "2026-05-01";
        summary.to = "2026-05-14";
        ReportNewPatient row = new ReportNewPatient();
        row.petName = "A";
        row.ownerName = "B";
        row.addedAt = "2026-05-02";
        summary.newPatients.add(row);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(summary);
        assertTrue(json.contains("\"newPatients\""));
        assertTrue(json.contains("\"petName\":\"A\""));
    }
}
