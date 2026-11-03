-- Optional product SKU consumed when a clinic service is performed (e.g. vaccine vial).
ALTER TABLE inventory_items
    ADD COLUMN consumable_sku VARCHAR(64) NULL;
