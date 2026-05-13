package com.bayport.dto;

/**
 * One new pet row in {@link ReportSummary#newPatients}.
 * Top-level type avoids inner-class loading issues ({@code ReportSummary$NewPatient}) in some deployments.
 */
public class ReportNewPatient {
    public String petName;
    public String ownerName;
    public String addedAt;
}
