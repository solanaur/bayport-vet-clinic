package com.bayport.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.bayport.util.MoneySerializer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Recent POS checkout for front-desk history panel. */
public class PosSaleHistoryDto {
    public long saleId;
    public String occurredAt;
    public String petName;

    @JsonSerialize(using = MoneySerializer.class)
    public BigDecimal total;

    public List<Line> lines = new ArrayList<>();

    public static class Line {
        public String itemName;
        public String sku;
        public int quantity;

        @JsonSerialize(using = MoneySerializer.class)
        public BigDecimal unitPrice;

        @JsonSerialize(using = MoneySerializer.class)
        public BigDecimal lineTotal;

        public String lineKind;
    }
}
