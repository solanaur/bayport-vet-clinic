package com.bayport.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Medication entity for prescription management.
 * Linked to procedures via ProcedureMedication join table.
 */
@Entity
@Table(name = "medications")
public class Medication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;
    private String dosage;
    private String unit; // e.g., "mg", "ml", "tablet"

    @Column(precision = 12, scale = 2)
    private BigDecimal unitPrice;

    private boolean active = true;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

