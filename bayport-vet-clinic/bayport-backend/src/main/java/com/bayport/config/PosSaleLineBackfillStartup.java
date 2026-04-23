package com.bayport.config;

import com.bayport.service.PosSaleLineBackfillService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Runs one-time (per startup) migration: POS {@link com.bayport.entity.Sale} rows without
 * {@link com.bayport.entity.SaleLine} get lines parsed from {@code note} so reports break out procedures vs products.
 */
@Component
public class PosSaleLineBackfillStartup {

    private final PosSaleLineBackfillService backfillService;

    public PosSaleLineBackfillStartup(PosSaleLineBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        backfillService.backfillMissingLines();
    }
}
