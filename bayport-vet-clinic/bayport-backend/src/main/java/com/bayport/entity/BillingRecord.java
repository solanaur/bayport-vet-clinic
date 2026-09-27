package com.bayport.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.bayport.util.MoneySerializer;
import com.bayport.util.MoneyUtils;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "billing_records")
public class BillingRecord {

    public enum Status { PENDING, PAID }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long petId;
    private Long ownerId;
    private String ownerName;
    private String petName;
    private String description;

    @Column(precision = 12, scale = 2)
    @JsonSerialize(using = MoneySerializer.class)
    private BigDecimal amount;

    @Column(name = "subtotal_amount", precision = 12, scale = 2)
    @JsonSerialize(using = MoneySerializer.class)
    private BigDecimal subtotalAmount;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    @JsonSerialize(using = MoneySerializer.class)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    private String referenceType;
    private Long referenceId;

    @Column(name = "consultation_id")
    private Long consultationId;

    @OneToMany(mappedBy = "billing", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("lineOrder ASC, id ASC")
    private List<BillingLine> lines = new ArrayList<>();

    private LocalDateTime issuedAt = LocalDateTime.now();
    private LocalDateTime paidAt;

    // Getters / setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPetId() { return petId; }
    public void setPetId(Long petId) { this.petId = petId; }

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getPetName() { return petName; }
    public void setPetName(String petName) { this.petName = petName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = MoneyUtils.normalize(amount); }

    public BigDecimal getSubtotalAmount() { return subtotalAmount; }
    public void setSubtotalAmount(BigDecimal subtotalAmount) { this.subtotalAmount = MoneyUtils.normalize(subtotalAmount); }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount == null ? BigDecimal.ZERO : MoneyUtils.normalize(discountAmount);
    }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }

    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }

    public Long getConsultationId() { return consultationId; }
    public void setConsultationId(Long consultationId) { this.consultationId = consultationId; }

    public List<BillingLine> getLines() { return lines; }
    public void setLines(List<BillingLine> lines) {
        this.lines = lines != null ? lines : new ArrayList<>();
    }

    public void addLine(BillingLine line) {
        if (this.lines == null) {
            this.lines = new ArrayList<>();
        }
        this.lines.add(line);
        line.setBilling(this);
    }

    /** Recalculates subtotal and amount from itemized service lines. */
    public void refreshTotalFromLines() {
        BigDecimal sum = BigDecimal.ZERO;
        if (lines != null) {
            for (BillingLine line : lines) {
                if (line.getServiceCost() != null) {
                    sum = sum.add(line.getServiceCost());
                }
            }
        }
        BigDecimal subtotal = MoneyUtils.normalize(sum);
        this.subtotalAmount = subtotal;
        BigDecimal discount = this.discountAmount != null ? this.discountAmount : BigDecimal.ZERO;
        this.amount = MoneyUtils.normalize(subtotal.subtract(discount));
    }

    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
}

