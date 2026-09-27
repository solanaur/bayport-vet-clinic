package com.bayport.service;

import com.bayport.entity.BillingRecord;
import com.bayport.entity.Consultation;
import com.bayport.entity.Pet;
import com.bayport.entity.Procedure;
import com.bayport.repository.BillingRecordRepository;
import com.bayport.util.MoneyUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {
    @Mock
    private BillingRecordRepository billingRecordRepository;
    @Mock
    private SalesService salesService;
    @InjectMocks
    private BillingService billingService;

    @Test
    void markPaid_recordsSaleOnlyWhenTransitioningToPaid() {
        BillingRecord pending = new BillingRecord();
        pending.setId(10L);
        pending.setStatus(BillingRecord.Status.PENDING);
        when(billingRecordRepository.findById(10L)).thenReturn(Optional.of(pending));
        when(billingRecordRepository.save(any(BillingRecord.class))).thenAnswer(i -> i.getArgument(0));

        BillingRecord saved = billingService.markPaid(10L);

        assertEquals(BillingRecord.Status.PAID, saved.getStatus());
        verify(salesService, times(1)).recordSale(any(BillingRecord.class), eq("Manual"), eq("Marked as paid"));
    }

    @Test
    void markPaid_doesNotDuplicateSaleIfAlreadyPaid() {
        BillingRecord paid = new BillingRecord();
        paid.setId(11L);
        paid.setStatus(BillingRecord.Status.PAID);
        when(billingRecordRepository.findById(11L)).thenReturn(Optional.of(paid));

        BillingRecord saved = billingService.markPaid(11L);

        assertEquals(BillingRecord.Status.PAID, saved.getStatus());
        verify(salesService, never()).recordSale(any(BillingRecord.class), anyString(), anyString());
    }

    @Test
    void billConsultation_createsOneInvoiceWithItemizedLinesAndDerivedTotal() {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setName("Buddy");
        pet.setOwnerId(2L);
        pet.setOwner("Ana");

        Consultation consultation = new Consultation();
        consultation.setId(9L);
        consultation.setConsultDate(LocalDate.of(2026, 9, 22));

        Procedure exam = new Procedure();
        exam.setId(11L);
        exam.setName("General Exam");
        exam.setCost(new BigDecimal("500"));
        exam.setVet("Dr. Cruz");
        exam.setConsultationId(9L);

        Procedure lab = new Procedure();
        lab.setId(12L);
        lab.setName("Lab Test");
        lab.setCost(new BigDecimal("800"));
        lab.setConsultationId(9L);

        Procedure meds = new Procedure();
        meds.setId(13L);
        meds.setName("Medication");
        meds.setCost(new BigDecimal("300"));
        meds.setConsultationId(9L);

        when(billingRecordRepository.save(any(BillingRecord.class))).thenAnswer(invocation -> {
            BillingRecord record = invocation.getArgument(0);
            record.setId(1042L);
            return record;
        });

        BillingRecord invoice = billingService.billConsultation(pet, consultation, List.of(exam, lab, meds));

        assertEquals(1042L, invoice.getId());
        assertEquals("CONSULTATION", invoice.getReferenceType());
        assertEquals(9L, invoice.getReferenceId());
        assertEquals(9L, invoice.getConsultationId());
        assertEquals(3, invoice.getLines().size());
        assertEquals("General Exam", invoice.getLines().get(0).getServiceName());
        assertEquals(MoneyUtils.normalize(new BigDecimal("500")), invoice.getLines().get(0).getServiceCost());
        assertEquals(MoneyUtils.normalize(new BigDecimal("1600")), invoice.getAmount());
        assertEquals(MoneyUtils.normalize(new BigDecimal("1600")), invoice.getSubtotalAmount());
    }

    @Test
    void billConsultation_skipsZeroCostServicesAndReturnsNullWhenNothingBillable() {
        Pet pet = new Pet();
        pet.setId(1L);
        Procedure free = new Procedure();
        free.setName("Follow-up");
        free.setCost(BigDecimal.ZERO);

        assertNull(billingService.billConsultation(pet, null, List.of(free)));
        verify(billingRecordRepository, never()).save(any(BillingRecord.class));
    }
}
