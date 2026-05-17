-- Rx template fields (idempotent; MySQL-compatible).
SET @db := DATABASE();

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'prescriptions' AND column_name = 'dispense_qty') > 0,
  'SELECT 1',
  'ALTER TABLE prescriptions ADD COLUMN dispense_qty INTEGER NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'prescriptions' AND column_name = 'diagnosis') > 0,
  'SELECT 1',
  'ALTER TABLE prescriptions ADD COLUMN diagnosis VARCHAR(255) NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'prescriptions' AND column_name = 'days_supply') > 0,
  'SELECT 1',
  'ALTER TABLE prescriptions ADD COLUMN days_supply VARCHAR(64) NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'prescriptions' AND column_name = 'rx_status') > 0,
  'SELECT 1',
  'ALTER TABLE prescriptions ADD COLUMN rx_status VARCHAR(20) DEFAULT ''SAVED'''
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'prescriptions' AND column_name = 'vet_notes') > 0,
  'SELECT 1',
  'ALTER TABLE prescriptions ADD COLUMN vet_notes VARCHAR(2000) NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
