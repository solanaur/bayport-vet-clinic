package com.bayport.dto;

import com.bayport.entity.BillingRecord;
import com.bayport.entity.Consultation;
import com.bayport.entity.Procedure;

import java.util.ArrayList;
import java.util.List;

public class ConsultationSaveResponse {
    private Consultation consultation;
    private List<Procedure> procedures = new ArrayList<>();
    private BillingRecord invoice;

    public Consultation getConsultation() { return consultation; }
    public void setConsultation(Consultation consultation) { this.consultation = consultation; }

    public List<Procedure> getProcedures() { return procedures; }
    public void setProcedures(List<Procedure> procedures) {
        this.procedures = procedures != null ? procedures : new ArrayList<>();
    }

    public BillingRecord getInvoice() { return invoice; }
    public void setInvoice(BillingRecord invoice) { this.invoice = invoice; }
}
