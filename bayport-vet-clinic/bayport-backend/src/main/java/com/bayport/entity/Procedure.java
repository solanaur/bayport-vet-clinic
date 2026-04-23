package com.bayport.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.bayport.util.MoneySerializer;
import com.bayport.util.MoneyUtils;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "procedures")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Procedure {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "procedure_date")
    private LocalDate performedAt;

    @Column(name = "procedure_name")
    private String name;

    private String notes;
    private String vet;

    // Extra fields to align with frontend automation
    private String category;
    private String labType;
    private String medications;

    @Column(precision = 12, scale = 2)
    @JsonSerialize(using = MoneySerializer.class)
    private BigDecimal cost;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id")
    private Pet pet;

    // Constructors
    public Procedure() {}

    public Procedure(LocalDate performedAt, String name, String notes, String vet) {
        this.performedAt = performedAt;
        this.name = name;
        this.notes = notes;
        this.vet = vet;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getPerformedAt() { return performedAt; }
    public void setPerformedAt(LocalDate performedAt) { this.performedAt = performedAt; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getVet() { return vet; }
    public void setVet(String vet) { this.vet = vet; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getLabType() { return labType; }
    public void setLabType(String labType) { this.labType = labType; }

    public String getMedications() { return medications; }
    public void setMedications(String medications) { this.medications = medications; }

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = MoneyUtils.normalize(cost); }

    public Pet getPet() { return pet; }
    public void setPet(Pet pet) { this.pet = pet; }
}

