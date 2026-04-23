package com.bayport.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.bayport.util.MoneySerializer;
import com.bayport.util.MoneyUtils;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "prescriptions")
public class Prescription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pet_id")
    private Long petId;

    private String pet;
    private String owner;
    private String drug;
    private String dosage;
    private String directions;
    /** Clinical / pharmacy notes (not pricing). */
    @Column(length = 2000)
    private String notes;
    private String prescriber;
    private LocalDate date;
    private boolean dispensed;

    @Column(name = "dispensed_at")
    private LocalDate dispensedAt;

    @Column(precision = 12, scale = 2)
    @JsonSerialize(using = MoneySerializer.class)
    private BigDecimal price;

    private boolean archived;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id", insertable = false, updatable = false)
    @JsonIgnore
    private Pet petEntity;

    // Constructors
    public Prescription() {}

    public Prescription(Long petId, String pet, String owner, String drug, String dosage,
                        String directions, String prescriber, LocalDate date) {
        this.petId = petId;
        this.pet = pet;
        this.owner = owner;
        this.drug = drug;
        this.dosage = dosage;
        this.directions = directions;
        this.prescriber = prescriber;
        this.date = date;
        this.dispensed = false;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPetId() { return petId; }
    public void setPetId(Long petId) { this.petId = petId; }

    public String getPet() { return pet; }
    public void setPet(String pet) { this.pet = pet; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getDrug() { return drug; }
    public void setDrug(String drug) { this.drug = drug; }

    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }

    public String getDirections() { return directions; }
    public void setDirections(String directions) { this.directions = directions; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getPrescriber() { return prescriber; }
    public void setPrescriber(String prescriber) { this.prescriber = prescriber; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public boolean isDispensed() { return dispensed; }
    public void setDispensed(boolean dispensed) { this.dispensed = dispensed; }

    public LocalDate getDispensedAt() { return dispensedAt; }
    public void setDispensedAt(LocalDate dispensedAt) { this.dispensedAt = dispensedAt; }

    public BigDecimal getPrice() { return price; }
    /** Null = no price on clinical Rx (POS handles billing). */
    public void setPrice(BigDecimal price) {
        this.price = price == null ? null : MoneyUtils.normalize(price);
    }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public Pet getPetEntity() { return petEntity; }
    public void setPetEntity(Pet petEntity) { this.petEntity = petEntity; }
}

