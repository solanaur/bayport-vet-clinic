-- `read` is a reserved word in MySQL; use `is_read` for the physical column.
-- MySQL 9: no ADD COLUMN IF NOT EXISTS; align pre-existing tables before indexes.
SET @db := DATABASE();

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = @db AND table_name = 'notifications') = 0,
  'SELECT 1',
  IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db AND table_name = 'notifications' AND column_name = 'is_read') > 0,
    'SELECT 1',
    'ALTER TABLE notifications ADD COLUMN is_read BOOLEAN NOT NULL DEFAULT FALSE'
  )
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = @db AND table_name = 'notifications') = 0,
  'SELECT 1',
  IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db AND table_name = 'notifications' AND column_name = 'read_at') > 0,
    'SELECT 1',
    'ALTER TABLE notifications ADD COLUMN read_at TIMESTAMP NULL'
  )
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = @db AND table_name = 'notifications') = 0,
  'SELECT 1',
  IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db AND table_name = 'notifications' AND column_name = 'created_at') > 0,
    'SELECT 1',
    'ALTER TABLE notifications ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP'
  )
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    message VARCHAR(500) NOT NULL,
    type VARCHAR(50) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = @db AND table_name = 'notifications' AND index_name = 'idx_notifications_user_id') > 0,
  'SELECT 1',
  'CREATE INDEX idx_notifications_user_id ON notifications(user_id)'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = @db AND table_name = 'notifications' AND index_name = 'idx_notifications_user_is_read') > 0,
  'SELECT 1',
  'CREATE INDEX idx_notifications_user_is_read ON notifications(user_id, is_read)'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
