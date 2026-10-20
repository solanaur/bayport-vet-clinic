-- Recoverable deletes, pet status, inventory expiry, and lookup indexes.
-- Adds columns only. Existing rows stay visible because deleted stays null.

SET @db := DATABASE();

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='owners' AND column_name='deleted')>0,'SELECT 1','ALTER TABLE owners ADD COLUMN deleted BIT(1) NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='owners' AND column_name='deleted_at')>0,'SELECT 1','ALTER TABLE owners ADD COLUMN deleted_at DATETIME(6) NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='owners' AND column_name='deleted_by')>0,'SELECT 1','ALTER TABLE owners ADD COLUMN deleted_by VARCHAR(100) NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='appointments' AND column_name='deleted')>0,'SELECT 1','ALTER TABLE appointments ADD COLUMN deleted BIT(1) NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='appointments' AND column_name='deleted_at')>0,'SELECT 1','ALTER TABLE appointments ADD COLUMN deleted_at DATETIME(6) NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='appointments' AND column_name='deleted_by')>0,'SELECT 1','ALTER TABLE appointments ADD COLUMN deleted_by VARCHAR(100) NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='consultations' AND column_name='deleted')>0,'SELECT 1','ALTER TABLE consultations ADD COLUMN deleted BIT(1) NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='consultations' AND column_name='deleted_at')>0,'SELECT 1','ALTER TABLE consultations ADD COLUMN deleted_at DATETIME(6) NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='consultations' AND column_name='deleted_by')>0,'SELECT 1','ALTER TABLE consultations ADD COLUMN deleted_by VARCHAR(100) NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='prescriptions' AND column_name='deleted')>0,'SELECT 1','ALTER TABLE prescriptions ADD COLUMN deleted BIT(1) NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='prescriptions' AND column_name='deleted_at')>0,'SELECT 1','ALTER TABLE prescriptions ADD COLUMN deleted_at DATETIME(6) NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='prescriptions' AND column_name='deleted_by')>0,'SELECT 1','ALTER TABLE prescriptions ADD COLUMN deleted_by VARCHAR(100) NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='billing_records' AND column_name='deleted')>0,'SELECT 1','ALTER TABLE billing_records ADD COLUMN deleted BIT(1) NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='billing_records' AND column_name='deleted_at')>0,'SELECT 1','ALTER TABLE billing_records ADD COLUMN deleted_at DATETIME(6) NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='billing_records' AND column_name='deleted_by')>0,'SELECT 1','ALTER TABLE billing_records ADD COLUMN deleted_by VARCHAR(100) NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='pets' AND column_name='record_status')>0,'SELECT 1','ALTER TABLE pets ADD COLUMN record_status VARCHAR(20) NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='inventory_items' AND column_name='batch_no')>0,'SELECT 1','ALTER TABLE inventory_items ADD COLUMN batch_no VARCHAR(64) NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='inventory_items' AND column_name='expiry_date')>0,'SELECT 1','ALTER TABLE inventory_items ADD COLUMN expiry_date DATE NULL');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=@db AND table_name='appointments' AND index_name='idx_appointments_vet_date')>0,'SELECT 1','CREATE INDEX idx_appointments_vet_date ON appointments (vet, `date`)');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=@db AND table_name='pets' AND index_name='idx_pets_owner')>0,'SELECT 1','CREATE INDEX idx_pets_owner ON pets (owner_id)');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=@db AND table_name='billing_records' AND index_name='idx_billing_pet')>0,'SELECT 1','CREATE INDEX idx_billing_pet ON billing_records (pet_id)');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=@db AND table_name='prescriptions' AND index_name='idx_prescriptions_pet')>0,'SELECT 1','CREATE INDEX idx_prescriptions_pet ON prescriptions (pet_id)');
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
