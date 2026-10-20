-- Phase 1 security columns and tables. MySQL-compatible. Does not delete clinical rows.
SET @db := DATABASE();

-- Widen totp_secret so AES-GCM ciphertext fits (legacy plaintext values are encrypted by the app).
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'users' AND column_name = 'totp_secret') = 0,
  'ALTER TABLE users ADD COLUMN totp_secret VARCHAR(512) NULL',
  'ALTER TABLE users MODIFY COLUMN totp_secret VARCHAR(512) NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'users' AND column_name = 'totp_last_step') > 0,
  'SELECT 1',
  'ALTER TABLE users ADD COLUMN totp_last_step BIGINT NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'users' AND column_name = 'token_version') > 0,
  'SELECT 1',
  'ALTER TABLE users ADD COLUMN token_version INT NULL DEFAULT 0'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
UPDATE users SET token_version = 0 WHERE token_version IS NULL;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'users' AND column_name = 'display_password') = 0,
  'SELECT 1',
  'ALTER TABLE users DROP COLUMN display_password'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

CREATE TABLE IF NOT EXISTS mfa_recovery_codes (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  code_hash VARCHAR(100) NOT NULL,
  used_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_mfa_recovery_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS login_lockouts (
  id BIGINT NOT NULL AUTO_INCREMENT,
  lock_key VARCHAR(200) NOT NULL,
  failure_count INT NOT NULL,
  locked_until_epoch BIGINT NOT NULL,
  next_allowed_epoch BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_login_lockouts_key (lock_key)
);

CREATE TABLE IF NOT EXISTS auth_rate_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  bucket_key VARCHAR(200) NOT NULL,
  event_epoch BIGINT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_auth_rate_bucket (bucket_key, event_epoch)
);
