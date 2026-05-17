package com.bayport.dto;

import java.math.BigDecimal;

/** One calendar day of POS activity for reports / PDF. */
public class ReportDailyPosRow {
    public String date;
    public int transactions;
    public BigDecimal total;
}
