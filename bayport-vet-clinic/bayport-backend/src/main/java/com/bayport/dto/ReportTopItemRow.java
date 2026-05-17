package com.bayport.dto;

import java.math.BigDecimal;

/** Aggregated POS line for “top selling items” on reports / PDF. */
public class ReportTopItemRow {
    public String name;
    public String category;
    public int qty;
    public BigDecimal total;
}
