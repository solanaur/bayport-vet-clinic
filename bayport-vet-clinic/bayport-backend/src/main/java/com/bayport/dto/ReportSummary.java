package com.bayport.dto;

import com.bayport.entity.OperationLog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ReportSummary {
    public String period;
    public String from;
    public String to;
    public int appointmentsDone;
    public int prescriptionsDispensed;
    public int petsAdded;
    public List<OperationLog> events = new ArrayList<>();
    public List<NewPatient> newPatients = new ArrayList<>();
    /** @deprecated No longer populated; use {@link #posProcedureLines} / {@link #posProductLines}. */
    @Deprecated
    public List<FinishedAppointment> finished = new ArrayList<>();
    /** @deprecated No longer populated. */
    @Deprecated
    public List<DispensedPrescription> dispensed = new ArrayList<>();
    /** Total POS checkout amount in period (all lines). */
    public BigDecimal totalProfit = BigDecimal.ZERO;
    /** Revenue from front-desk POS checkouts in the selected period. */
    public BigDecimal posSales = BigDecimal.ZERO;
    /** POS line items classified as clinic procedures / services (SERVICE inventory). */
    public List<PosSaleLineRow> posProcedureLines = new ArrayList<>();
    /** POS line items classified as shop products (non-service inventory). */
    public List<PosSaleLineRow> posProductLines = new ArrayList<>();
    public BigDecimal posProcedureRevenue = BigDecimal.ZERO;
    public BigDecimal posProductRevenue = BigDecimal.ZERO;

    /** One row in POS Procedures or POS Products tables. */
    public static class PosSaleLineRow {
        public String date;
        public Long saleId;
        public String petName;
        public String itemName;
        public String sku;
        public int quantity;
        public BigDecimal unitPrice;
        public BigDecimal lineTotal;
    }

    public static class NewPatient {
        public String petName;
        public String ownerName;
        public String addedAt;
    }

    public static class FinishedAppointment {
        public String code;
        public String date;
        public String time;
        public String vet;
        public String pet;
        public String owner;
        public List<String> procedures = new ArrayList<>();
        public BigDecimal cost;
    }

    public static class DispensedPrescription {
        public String date;
        public String pet;
        public String owner;
        public String product;
        public String prescriber;
        public BigDecimal price;
    }
}

