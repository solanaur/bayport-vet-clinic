package com.bayport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * SKUs the user removed from inventory; catalog merge must not re-insert them on restart.
 */
@Entity
@Table(name = "inventory_sku_suppression")
public class InventorySkuSuppression {

    /** Normalized uppercase trimmed SKU (matches catalog merge checks). */
    @Id
    @Column(nullable = false, length = 128)
    private String sku;

    private LocalDateTime createdAt;

    public InventorySkuSuppression() {
    }

    public InventorySkuSuppression(String sku) {
        this.sku = sku;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
