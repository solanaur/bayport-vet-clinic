-- Pet last vaccination + prescription print / license fields (MySQL, idempotent).

SET @db := DATABASE();

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'pets' AND column_name = 'last_vaccination_date') > 0,
  'SELECT 1',
  'ALTER TABLE pets ADD COLUMN last_vaccination_date DATE NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'pets' AND column_name = 'last_vaccination_place') > 0,
  'SELECT 1',
  'ALTER TABLE pets ADD COLUMN last_vaccination_place VARCHAR(255) NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'pets' AND column_name = 'last_vaccination_vet') > 0,
  'SELECT 1',
  'ALTER TABLE pets ADD COLUMN last_vaccination_vet VARCHAR(255) NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'prescriptions' AND column_name = 'prescriber_license_no') > 0,
  'SELECT 1',
  'ALTER TABLE prescriptions ADD COLUMN prescriber_license_no VARCHAR(64) NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'prescriptions' AND column_name = 'printed_at') > 0,
  'SELECT 1',
  'ALTER TABLE prescriptions ADD COLUMN printed_at DATETIME(6) NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'prescriptions' AND column_name = 'print_count') > 0,
  'SELECT 1',
  'ALTER TABLE prescriptions ADD COLUMN print_count INT DEFAULT 0'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
