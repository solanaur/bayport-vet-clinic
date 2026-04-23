package com.bayport.dto;

/**
 * DTO used for incoming appointment create/update requests.
 * The client should send a petId and simple scalar fields instead of
 * embedding a full {@code Pet} object.
 */
public class AppointmentRequest {

    private Long petId;
    private String owner;
    private String date;   // ISO-8601 string: YYYY-MM-DD
    private String time;   // HH:mm
    private String vet;    // Vet display name or username
    private String status; // Optional, server may override (e.g. "Pending")

    public Long getPetId() {
        return petId;
    }

    public void setPetId(Long petId) {
        this.petId = petId;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getVet() {
        return vet;
    }

    public void setVet(String vet) {
        this.vet = vet;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}


