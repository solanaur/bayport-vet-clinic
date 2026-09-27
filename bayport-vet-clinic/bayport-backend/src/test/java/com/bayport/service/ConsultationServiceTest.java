package com.bayport.service;

import com.bayport.dto.ConsultationCreateRequest;
import com.bayport.dto.ConsultationSaveResponse;
import com.bayport.dto.ConsultationServiceRequest;
import com.bayport.entity.BillingRecord;
import com.bayport.entity.Consultation;
import com.bayport.entity.Pet;
import com.bayport.entity.Procedure;
import com.bayport.repository.ConsultationRepository;
import com.bayport.repository.PetRepository;
import com.bayport.repository.ProcedureRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationServiceTest {

    @Mock
    private ConsultationRepository consultationRepository;
    @Mock
    private ProcedureRepository procedureRepository;
    @Mock
    private PetRepository petRepository;
    @Mock
    private BayportService bayportService;
    @Mock
    private BillingService billingService;
    @InjectMocks
    private ConsultationService consultationService;

    @Test
    void saveConsultation_createsParentVisitThenOneAggregatedInvoice() {
        Pet pet = new Pet();
        pet.setId(5L);
        pet.setName("Buddy");
        when(petRepository.findById(5L)).thenReturn(Optional.of(pet));
        when(consultationRepository.save(any(Consultation.class))).thenAnswer(invocation -> {
            Consultation c = invocation.getArgument(0);
            c.setId(9L);
            return c;
        });

        Procedure exam = new Procedure();
        exam.setId(11L);
        exam.setName("General Exam");
        exam.setCost(new BigDecimal("500"));
        Procedure lab = new Procedure();
        lab.setId(12L);
        lab.setName("Lab Test");
        lab.setCost(new BigDecimal("800"));
        when(procedureRepository.findByConsultationIdOrderByIdAsc(9L)).thenReturn(List.of(exam, lab));

        BillingRecord invoice = new BillingRecord();
        invoice.setId(1042L);
        when(billingService.billConsultation(eq(pet), any(Consultation.class), eq(List.of(exam, lab))))
                .thenReturn(invoice);

        ConsultationCreateRequest request = new ConsultationCreateRequest();
        request.setPetId(5L);
        request.setDate("2026-09-22");
        request.setDiagnosis("Checkup");
        ConsultationServiceRequest s1 = new ConsultationServiceRequest();
        s1.setName("General Exam");
        s1.setCost(new BigDecimal("500"));
        ConsultationServiceRequest s2 = new ConsultationServiceRequest();
        s2.setName("Lab Test");
        s2.setCost(new BigDecimal("800"));
        request.setServices(List.of(s1, s2));

        ConsultationSaveResponse saved = consultationService.saveConsultation(request);

        assertEquals(9L, saved.getConsultation().getId());
        assertEquals(2, saved.getProcedures().size());
        assertSame(invoice, saved.getInvoice());
        verify(bayportService, times(2)).addProcedureToPet(eq(5L), any(Procedure.class));
        verify(billingService, times(1)).billConsultation(eq(pet), any(Consultation.class), eq(List.of(exam, lab)));
    }
}
