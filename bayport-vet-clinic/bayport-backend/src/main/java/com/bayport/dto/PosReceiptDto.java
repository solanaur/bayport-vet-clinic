package com.bayport.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class PosReceiptDto {
    public Long saleId;
    public String occurredAt;
    public String petName;
    public BigDecimal total;
    public String paymentMethod;
    public String note;
    public List<Line> lines = new ArrayList<>();

    public static class Line {
        public String itemName;
        public String sku;
        public int quantity;
        public BigDecimal unitPrice;
        public BigDecimal lineTotal;
    }
}
