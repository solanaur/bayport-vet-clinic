package com.bayport.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.bayport.util.MoneySerializer;
import com.bayport.util.MoneyUtils;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "billing_lines")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class BillingLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_record_id", nullable = false)
    private BillingRecord billing;

    @Column(name = "consultation_id")
    private Long consultationId;

    @Column(name = "procedure_id")
    private Long procedureId;

    @Column(name = "service_name", nullable = false, length = 255)
    private String serviceName;

    @Column(name = "service_cost", nullable = false, precision = 14, scale = 2)
    @JsonSerialize(using = MoneySerializer.class)
    private BigDecimal serviceCost;

    @Column(name = "performed_by", length = 120)
    private String performedBy;

    @Column(name = "line_order", nullable = false)
    private int lineOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public BillingRecord getBilling() { return billing; }
    public void setBilling(BillingRecord billing) { this.billing = billing; }

    public Long getConsultationId() { return consultationId; }
    public void setConsultationId(Long consultationId) { this.consultationId = consultationId; }

    public Long getProcedureId() { return procedureId; }
    public void setProcedureId(Long procedureId) { this.procedureId = procedureId; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public BigDecimal getServiceCost() { return serviceCost; }
    public void setServiceCost(BigDecimal serviceCost) { this.serviceCost = MoneyUtils.normalize(serviceCost); }

    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

    public int getLineOrder() { return lineOrder; }
    public void setLineOrder(int lineOrder) { this.lineOrder = lineOrder; }
}
