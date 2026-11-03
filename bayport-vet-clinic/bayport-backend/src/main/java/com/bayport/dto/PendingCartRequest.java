package com.bayport.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Cart payload for saving a Pending invoice without collecting payment or deducting stock.
 */
public class PendingCartRequest {

    private Long petId;
    private BigDecimal discountAmount;
    private List<PosLineRequest> lines = new ArrayList<>();

    public Long getPetId() {
        return petId;
    }

    public void setPetId(Long petId) {
        this.petId = petId;
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
}
