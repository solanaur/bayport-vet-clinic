-- Pet age label (e.g. "10 months") and billing discount/subtotal for invoices
ALTER TABLE pets ADD COLUMN IF NOT EXISTS age_display VARCHAR(64);

ALTER TABLE billing_records ADD COLUMN IF NOT EXISTS subtotal_amount NUMERIC(12, 2);
ALTER TABLE billing_records ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(12, 2) DEFAULT 0;
