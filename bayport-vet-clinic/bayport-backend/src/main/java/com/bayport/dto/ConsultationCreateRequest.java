package com.bayport.dto;

import java.util.ArrayList;
import java.util.List;

public class ConsultationCreateRequest {
    private Long petId;
    private Long appointmentId;
    private String date;
    private String time;
    private String diagnosis;
    private String notes;
    private String vet;
    private List<ConsultationServiceRequest> services = new ArrayList<>();

    public Long getPetId() { return petId; }
    public void setPetId(Long petId) { this.petId = petId; }

    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getVet() { return vet; }
    public void setVet(String vet) { this.vet = vet; }

    public List<ConsultationServiceRequest> getServices() { return services; }
    public void setServices(List<ConsultationServiceRequest> services) {
        this.services = services != null ? services : new ArrayList<>();
    }
}
