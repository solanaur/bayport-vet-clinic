package com.bayport.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

/**
 * Front-desk POS checkout: pricing + stock + payment; optional Rx fulfillment.
 */
public class PosCheckoutRequest {

    private Long petId;
    private String paymentMethod; // Cash, Card, GCash, etc.

    @NotEmpty
    @Valid
    private List<PosLineRequest> lines = new ArrayList<>();

    /** Optional: mark these prescriptions dispensed after sale (same pet only). */
    private List<Long> fulfillPrescriptionIds = new ArrayList<>();

    public Long getPetId() {
        return petId;
    }

    public void setPetId(Long petId) {
        this.petId = petId;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public List<PosLineRequest> getLines() {
        return lines;
    }

    public void setLines(List<PosLineRequest> lines) {
        this.lines = lines != null ? lines : new ArrayList<>();
    }

    public List<Long> getFulfillPrescriptionIds() {
        return fulfillPrescriptionIds;
    }

    public void setFulfillPrescriptionIds(List<Long> fulfillPrescriptionIds) {
        this.fulfillPrescriptionIds = fulfillPrescriptionIds != null ? fulfillPrescriptionIds : new ArrayList<>();
    }
}
