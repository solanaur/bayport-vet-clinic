package com.bayport.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(
        value = {"ownerEntity", "hibernateLazyInitializer", "handler"},
        allowSetters = true
)
@Entity
@Table(name = "pets")
public class Pet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    private String species;
    private String breed;
    private String gender;
    private Integer age;

    @Column(name = "age_display", length = 64)
    private String ageDisplay;

    private String microchip;

    private String color;

    private String weight;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "owner_name")
    private String owner;

    @Column(name = "owner_id")
    private Long ownerId;

    private String address;
    private String federation;
    private String photo;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Soft delete fields
    @Column(nullable = true, columnDefinition = "BOOLEAN DEFAULT FALSE") // Allow null for existing pets
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    @Column(name = "last_vaccination_date")
    private LocalDate lastVaccinationDate;

    @Column(name = "last_vaccination_place")
    private String lastVaccinationPlace;

    @Column(name = "last_vaccination_vet")
    private String lastVaccinationVet;

    @Column(length = 500)
    private String allergies;

    @Column(name = "chronic_conditions", length = 500)
    private String chronicConditions;

    @Column(name = "known_medications", columnDefinition = "TEXT")
    private String knownMedications;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", insertable = false, updatable = false)
    private Owner ownerEntity;

    @JsonManagedReference
    @OneToMany(mappedBy = "pet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Procedure> procedures = new ArrayList<>();

    // Constructors
    public Pet() {}

    public Pet(String name, String species, String breed, String gender, Integer age,
               String microchip, String owner, String address, String federation) {
        this.name = name;
        this.species = species;
        this.breed = breed;
        this.gender = gender;
        this.age = age;
        this.microchip = microchip;
        this.owner = owner;
        this.address = address;
        this.federation = federation;
    }

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }

    public String getBreed() { return breed; }
    public void setBreed(String breed) { this.breed = breed; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getAgeDisplay() { return ageDisplay; }
    public void setAgeDisplay(String ageDisplay) { this.ageDisplay = ageDisplay; }

    public String getMicrochip() { return microchip; }
    public void setMicrochip(String microchip) { this.microchip = microchip; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getWeight() { return weight; }
    public void setWeight(String weight) { this.weight = weight; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getFederation() { return federation; }
    public void setFederation(String federation) { this.federation = federation; }

    public String getPhoto() { return photo; }
    public void setPhoto(String photo) { this.photo = photo; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Owner getOwnerEntity() { return ownerEntity; }
    public void setOwnerEntity(Owner ownerEntity) { this.ownerEntity = ownerEntity; }

    public List<Procedure> getProcedures() { return procedures; }
    public void setProcedures(List<Procedure> procedures) { this.procedures = procedures; }

    // Soft delete getters and setters
    public Boolean getDeleted() { return deleted != null ? deleted : false; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public String getDeletedBy() { return deletedBy; }
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }

    public LocalDate getLastVaccinationDate() { return lastVaccinationDate; }
    public void setLastVaccinationDate(LocalDate lastVaccinationDate) { this.lastVaccinationDate = lastVaccinationDate; }

    public String getLastVaccinationPlace() { return lastVaccinationPlace; }
    public void setLastVaccinationPlace(String lastVaccinationPlace) { this.lastVaccinationPlace = lastVaccinationPlace; }

    public String getLastVaccinationVet() { return lastVaccinationVet; }
    public void setLastVaccinationVet(String lastVaccinationVet) { this.lastVaccinationVet = lastVaccinationVet; }

    public String getAllergies() { return allergies; }
    public void setAllergies(String allergies) { this.allergies = allergies; }

    public String getChronicConditions() { return chronicConditions; }
    public void setChronicConditions(String chronicConditions) { this.chronicConditions = chronicConditions; }

    public String getKnownMedications() { return knownMedications; }
    public void setKnownMedications(String knownMedications) { this.knownMedications = knownMedications; }
}

