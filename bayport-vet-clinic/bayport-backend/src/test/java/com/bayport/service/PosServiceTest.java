package com.bayport.service;

import com.bayport.dto.PendingCartRequest;
import com.bayport.dto.PosCheckoutRequest;
import com.bayport.dto.PosLineRequest;
import com.bayport.entity.Appointment;
import com.bayport.entity.BillingRecord;
import com.bayport.entity.InventoryItem;
import com.bayport.entity.Pet;
import com.bayport.entity.Sale;
import com.bayport.repository.PetRepository;
import com.bayport.repository.PrescriptionRepository;
import com.bayport.repository.SaleLineRepository;
import com.bayport.repository.SaleRepository;
import com.bayport.config.InventoryCatalogImporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosServiceTest {
    @Mock
    private InventoryService inventoryService;
    @Mock
    private SalesService salesService;
    @Mock
    private PrescriptionRepository prescriptionRepository;
    @Mock
    private PetRepository petRepository;
    @Mock
    private BayportService bayportService;
    @Mock
    private InventoryCatalogImporter inventoryCatalogImporter;
    @Mock
    private SaleLineRepository saleLineRepository;
    @Mock
    private SaleRepository saleRepository;
    @Mock
    private PosSaleLineBackfillService posSaleLineBackfillService;
    @Mock
    private com.bayport.repository.BillingRecordRepository billingRecordRepository;

    @InjectMocks
    private PosService posService;

    @Test
    void checkout_rejectsEmptyCart() {
        PosCheckoutRequest req = new PosCheckoutRequest();
        req.setLines(Collections.emptyList());
        assertThrows(IllegalArgumentException.class, () -> posService.checkout(req));
    }

    @Test
    void savePending_createsPendingInvoiceWithoutStockDeduction() {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setName("Choco");
        pet.setOwnerId(2L);
        pet.setOwner("Maria");
        when(petRepository.findById(1L)).thenReturn(Optional.of(pet));

        InventoryItem item = new InventoryItem();
        item.setId(9L);
        item.setName("Vaccine");
        item.setUnitPrice(new BigDecimal("500.00"));
        item.setQuantity(10);
        when(inventoryService.get(9L)).thenReturn(item);
        when(billingRecordRepository.save(any(BillingRecord.class))).thenAnswer(i -> {
            BillingRecord r = i.getArgument(0);
            r.setId(77L);
            return r;
        });

        PosLineRequest line = new PosLineRequest();
        line.setInventoryItemId(9L);
        line.setQuantity(2);

        PendingCartRequest req = new PendingCartRequest();
        req.setPetId(1L);
        req.setDiscountAmount(new BigDecimal("50"));
        req.setLines(List.of(line));

        BillingRecord saved = posService.savePending(req);

        assertEquals(BillingRecord.Status.PENDING, saved.getStatus());
        assertEquals(77L, saved.getId());
        assertEquals(1, saved.getLines().size());
        assertEquals(9L, saved.getLines().get(0).getInventoryItemId());
        assertEquals(2, saved.getLines().get(0).getQuantity());
        verify(inventoryService, never()).decreaseQuantityForSale(any(Long.class), org.mockito.ArgumentMatchers.anyInt());
        verify(inventoryService, never()).applySaleStock(any(), org.mockito.ArgumentMatchers.anyInt(), any());
        verify(salesService, never()).recordPosSale(any());
    }

    @Test
    void checkout_ignoresAppointmentIdWhenPetDoesNotMatch() {
        Appointment appt = new Appointment();
        appt.setId(5L);
        appt.setPetId(1L);
        when(bayportService.getAppointmentById(5L)).thenReturn(Optional.of(appt));

        PosLineRequest line = new PosLineRequest();
        line.setCustomName("Consultation Fee");
        line.setCustomAmount(new BigDecimal("350"));
        line.setQuantity(1);

        PosCheckoutRequest req = new PosCheckoutRequest();
        req.setPetId(99L);
        req.setAppointmentId(5L);
        req.setPaymentMethod("Cash");
        req.setLines(List.of(line));

        Sale sale = new Sale();
        sale.setId(100L);
        when(salesService.recordPosSale(any())).thenReturn(sale);
        when(billingRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        posService.checkout(req);

        verify(bayportService).tryMarkAppointmentDoneAfterPosCheckout(eq(null), eq(99L));
    }
}
