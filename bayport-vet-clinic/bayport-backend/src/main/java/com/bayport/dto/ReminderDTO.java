package com.bayport.dto;

import com.bayport.entity.Reminder;

public class ReminderDTO {

    private Long id;
    private Long petId;
    private Long ownerId;
    private String petName;
    private String ownerName;
    private String ownerEmail;
    private String date;
    private String message;
    private boolean sent;
    private String sentAt;
    private String type;
    private String targetEmail;

    // ----- Getters & Setters -----

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public Long getPetId() {
        return petId;
    }
    public void setPetId(Long petId) {
        this.petId = petId;
    }

    public Long getOwnerId() {
        return ownerId;
    }
    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getPetName() {
        return petName;
    }
    public void setPetName(String petName) {
        this.petName = petName;
    }

    public String getOwnerName() {
        return ownerName;
    }
    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getDate() {
        return date;
    }
    public void setDate(String date) {
        this.date = date;
    }

    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSent() {
        return sent;
    }
    public void setSent(boolean sent) {
        this.sent = sent;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }

    public String getSentAt() { return sentAt; }
    public void setSentAt(String sentAt) { this.sentAt = sentAt; }

    public String getTargetEmail() { return targetEmail; }
    public void setTargetEmail(String targetEmail) { this.targetEmail = targetEmail; }

    // ----- Factory method to build from entity -----

    public static ReminderDTO from(Reminder r, String petName, String ownerName, String ownerEmail) {
        ReminderDTO dto = new ReminderDTO();
        dto.setId(r.getId());
        dto.setPetId(r.getPetId());
        dto.setOwnerId(r.getOwnerId());
        dto.setDate(r.getDate() != null ? r.getDate().toString() : null);
        dto.setMessage(r.getMessage());
        dto.setSent(r.isSent());
        dto.setPetName(petName);
        dto.setOwnerName(ownerName);
        dto.setOwnerEmail(ownerEmail);
        dto.setType(r.getType() != null ? r.getType().name() : null);
        dto.setSentAt(r.getSentAt() != null ? r.getSentAt().toString() : null);
        dto.setTargetEmail(r.getTargetEmail());
        return dto;
    }
}
