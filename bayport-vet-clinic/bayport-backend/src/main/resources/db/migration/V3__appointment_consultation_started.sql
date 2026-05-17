-- When a consultation is active (approved), elapsed time is computed from this timestamp.
SET @db := DATABASE();
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'appointments' AND column_name = 'consultation_started_at') > 0,
  'SELECT 1',
  'ALTER TABLE appointments ADD COLUMN consultation_started_at DATETIME(6) NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
