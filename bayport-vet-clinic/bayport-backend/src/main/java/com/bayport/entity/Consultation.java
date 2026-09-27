package com.bayport.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "consultations")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Consultation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pet_id", nullable = false)
    private Long petId;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "consult_date", nullable = false)
    private LocalDate consultDate;

    @Column(name = "consult_time", length = 16)
    private String consultTime;

    @Column(length = 500)
    private String diagnosis;

    @Column(length = 4000)
    private String notes;

    @Column(length = 120)
    private String vet;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPetId() { return petId; }
    public void setPetId(Long petId) { this.petId = petId; }

    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }

    public LocalDate getConsultDate() { return consultDate; }
    public void setConsultDate(LocalDate consultDate) { this.consultDate = consultDate; }

    public String getConsultTime() { return consultTime; }
    public void setConsultTime(String consultTime) { this.consultTime = consultTime; }

    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getVet() { return vet; }
    public void setVet(String vet) { this.vet = vet; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
