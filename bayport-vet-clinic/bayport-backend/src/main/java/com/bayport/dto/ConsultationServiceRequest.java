package com.bayport.dto;

import java.math.BigDecimal;

public class ConsultationServiceRequest {
    private String name;
    private String category;
    private BigDecimal cost;
    private String labType;
    private String medications;
    private String notes;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }

    public String getLabType() { return labType; }
    public void setLabType(String labType) { this.labType = labType; }

    public String getMedications() { return medications; }
    public void setMedications(String medications) { this.medications = medications; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
