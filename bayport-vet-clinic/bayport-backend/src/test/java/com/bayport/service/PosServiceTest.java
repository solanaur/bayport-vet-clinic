package com.bayport.service;

import com.bayport.dto.PosCheckoutRequest;
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

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @InjectMocks
    private PosService posService;

    @Test
    void checkout_rejectsEmptyCart() {
        PosCheckoutRequest req = new PosCheckoutRequest();
        req.setLines(Collections.emptyList());
        assertThrows(IllegalArgumentException.class, () -> posService.checkout(req));
    }
}
