-- Idempotent column adds for older installs (MySQL 9 compatible).
SET @db := DATABASE();

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'notifications' AND column_name = 'is_read') > 0,
  'SELECT 1',
  'ALTER TABLE notifications ADD COLUMN is_read BOOLEAN NOT NULL DEFAULT FALSE'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'notifications' AND column_name = 'read_at') > 0,
  'SELECT 1',
  'ALTER TABLE notifications ADD COLUMN read_at TIMESTAMP NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'notifications' AND column_name = 'created_at') > 0,
  'SELECT 1',
  'ALTER TABLE notifications ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
