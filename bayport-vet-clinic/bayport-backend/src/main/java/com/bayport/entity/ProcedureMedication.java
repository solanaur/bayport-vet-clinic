package com.bayport.entity;

import jakarta.persistence.*;

/**
 * Join entity for many-to-many relationship between Procedure and Medication.
 * Represents which medications are associated with which procedures.
 */
@Entity
@Table(name = "procedure_medications")
public class ProcedureMedication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "procedure_id", nullable = false)
    private Procedure procedure;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    private String dosageOverride; // Optional: override default medication dosage for this procedure
    private String instructions; // Additional instructions for this specific procedure-medication combination

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Procedure getProcedure() { return procedure; }
    public void setProcedure(Procedure procedure) { this.procedure = procedure; }

    public Medication getMedication() { return medication; }
    public void setMedication(Medication medication) { this.medication = medication; }

    public String getDosageOverride() { return dosageOverride; }
    public void setDosageOverride(String dosageOverride) { this.dosageOverride = dosageOverride; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
}

