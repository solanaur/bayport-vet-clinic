package com.bayport.service;

import com.bayport.dto.PosCheckoutRequest;
import com.bayport.dto.PosLineRequest;
import com.bayport.entity.Appointment;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

        posService.checkout(req);

        verify(bayportService).tryMarkAppointmentDoneAfterPosCheckout(eq(null), eq(99L));
    }
}
