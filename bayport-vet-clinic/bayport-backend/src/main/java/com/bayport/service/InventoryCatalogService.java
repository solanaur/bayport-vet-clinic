package com.bayport.service;

import com.bayport.config.InventoryCatalogImporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs catalog merge in its own transaction (not bundled with {@code DataInitializer}'s seed TX).
 */
@Service
public class InventoryCatalogService {

    private static final Logger log = LoggerFactory.getLogger(InventoryCatalogService.class);

    private final InventoryCatalogImporter inventoryCatalogImporter;

    public InventoryCatalogService(InventoryCatalogImporter inventoryCatalogImporter) {
        this.inventoryCatalogImporter = inventoryCatalogImporter;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void importMissingSkus() {
        log.debug("Starting inventory catalog merge (REQUIRES_NEW transaction)…");
        inventoryCatalogImporter.importMissingSkus();
    }
}
