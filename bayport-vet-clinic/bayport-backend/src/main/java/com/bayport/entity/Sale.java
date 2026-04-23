package com.bayport.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.bayport.util.MoneySerializer;
import com.bayport.util.MoneyUtils;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales")
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long billingId;
    private Long petId;
    private String petName;
    private String source; // Procedure, Pharmacy, etc.
    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(precision = 12, scale = 2)
    @JsonSerialize(using = MoneySerializer.class)
    private BigDecimal amount;

    private LocalDateTime occurredAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBillingId() { return billingId; }
    public void setBillingId(Long billingId) { this.billingId = billingId; }

    public Long getPetId() { return petId; }
    public void setPetId(Long petId) { this.petId = petId; }

    public String getPetName() { return petName; }
    public void setPetName(String petName) { this.petName = petName; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = MoneyUtils.normalize(amount); }

    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
}

