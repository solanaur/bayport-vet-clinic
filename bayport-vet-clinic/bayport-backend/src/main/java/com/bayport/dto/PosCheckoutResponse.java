package com.bayport.dto;

import java.math.BigDecimal;

public class PosCheckoutResponse {

    private Long saleId;
    private BigDecimal total;
    private String note;

    public PosCheckoutResponse() {
    }

    public PosCheckoutResponse(Long saleId, BigDecimal total, String note) {
        this.saleId = saleId;
        this.total = total;
        this.note = note;
    }

    public Long getSaleId() {
        return saleId;
    }

    public void setSaleId(Long saleId) {
        this.saleId = saleId;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
