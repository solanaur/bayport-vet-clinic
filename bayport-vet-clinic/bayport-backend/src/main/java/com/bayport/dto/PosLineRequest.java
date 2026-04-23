package com.bayport.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * One POS line: either {@code inventoryItemId} (shop stock) or {@code sku} (clinic fee catalog SVC-*).
 * Optional {@code unitPriceOverride} for quoted surgery / custom fees.
 */
public class PosLineRequest {

    /** Set for medications & products from inventory list. */
    private Long inventoryItemId;

    /** Set for procedures (e.g. SVC-001) — row is auto-created from catalog JSON if missing. */
    private String sku;

    /** When set, used instead of inventory unit price (e.g. agreed surgical quote). */
    private BigDecimal unitPriceOverride;

    @NotNull
    @Min(1)
    private Integer quantity;

    public Long getInventoryItemId() {
        return inventoryItemId;
    }

    public void setInventoryItemId(Long inventoryItemId) {
        this.inventoryItemId = inventoryItemId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public BigDecimal getUnitPriceOverride() {
        return unitPriceOverride;
    }

    public void setUnitPriceOverride(BigDecimal unitPriceOverride) {
        this.unitPriceOverride = unitPriceOverride;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
