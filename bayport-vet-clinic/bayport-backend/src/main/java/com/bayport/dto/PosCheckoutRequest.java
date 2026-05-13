package com.bayport.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Front-desk POS checkout: pricing + stock + payment; optional Rx fulfillment.
 */
public class PosCheckoutRequest {

    private Long petId;

    /**
     * When set, marks this appointment {@code Done} after a successful checkout
     * (same browser session links consultation → POS).
     */
    private Long appointmentId;

    private String paymentMethod; // Cash, Card, GCash, etc.

    /** Optional flat discount in peso (applied after line totals, before recording sale). */
    private BigDecimal discountAmount;

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

    public Long getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(Long appointmentId) {
        this.appointmentId = appointmentId;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
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
