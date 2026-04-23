package com.bayport.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.bayport.util.MoneySerializer;
import com.bayport.util.MoneyUtils;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * One line on a {@link Sale} — used to split POS revenue into procedures vs products on reports.
 */
@Entity
@Table(name = "sale_lines")
public class SaleLine {

    public static final String KIND_PROCEDURE = "PROCEDURE";
    public static final String KIND_PRODUCT = "PRODUCT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    /** {@link #KIND_PROCEDURE} or {@link #KIND_PRODUCT} */
    @Column(nullable = false, length = 20)
    private String lineKind;

    @Column(nullable = false, length = 512)
    private String itemName;

    @Column(length = 64)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    @Column(precision = 12, scale = 2)
    @JsonSerialize(using = MoneySerializer.class)
    private BigDecimal unitPrice;

    @Column(precision = 12, scale = 2)
    @JsonSerialize(using = MoneySerializer.class)
    private BigDecimal lineTotal;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Sale getSale() {
        return sale;
    }

    public void setSale(Sale sale) {
        this.sale = sale;
    }

    public String getLineKind() {
        return lineKind;
    }

    public void setLineKind(String lineKind) {
        this.lineKind = lineKind;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice != null ? MoneyUtils.normalize(unitPrice) : null;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal != null ? MoneyUtils.normalize(lineTotal) : null;
    }
}
