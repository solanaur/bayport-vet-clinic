package com.bayport.service;

import com.bayport.entity.BillingRecord;
import com.bayport.repository.BillingRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
