package com.bayport.service;

import com.bayport.entity.InventoryItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryRulesTest {

    @Test
    void servicesAreDetectedBySkuOrCategory() {
        assertTrue(InventoryRules.isService("SVC-007", "Vaccination"));
        assertTrue(InventoryRules.isService(null, "Consultation"));
        assertTrue(InventoryRules.isService(null, "SERVICE"));
        assertFalse(InventoryRules.isService("VACCINES-001", "VACCINES"));
    }

    @Test
    void vaccineServicesMapToProductConsumables() {
        InventoryItem service = new InventoryItem();
        service.setSku("SVC-007");
        service.setCategory("Vaccination");
        assertEquals("VACCINES-001", InventoryRules.consumableSkuFor(service));

        InventoryItem product = new InventoryItem();
        product.setSku("VACCINES-001");
        product.setCategory("VACCINES");
        assertEquals(null, InventoryRules.consumableSkuFor(product));
    }
}
