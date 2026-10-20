-- Opt-out, invoice void details, and an inventory version for concurrent stock updates.

SET @db := DATABASE();

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='owners' AND column_name='reminder_opt_out')>0,'SELECT 1','ALTER TABLE owners ADD COLUMN reminder_opt_out BIT(1) NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='billing_records' AND column_name='void_reason')>0,'SELECT 1','ALTER TABLE billing_records ADD COLUMN void_reason VARCHAR(255) NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='billing_records' AND column_name='voided_at')>0,'SELECT 1','ALTER TABLE billing_records ADD COLUMN voided_at DATETIME(6) NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='inventory_items' AND column_name='version')>0,'SELECT 1','ALTER TABLE inventory_items ADD COLUMN version BIGINT NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
UPDATE inventory_items SET version = 0 WHERE version IS NULL;
