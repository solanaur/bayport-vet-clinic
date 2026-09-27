-- Security hardening (MySQL-compatible). H2/desktop skips Flyway and uses ddl-auto=update.
SET @db := DATABASE();

-- pets.assigned_veterinarian_id
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'pets' AND column_name = 'assigned_veterinarian_id') > 0,
  'SELECT 1',
  'ALTER TABLE pets ADD COLUMN assigned_veterinarian_id BIGINT NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

-- mfa_code.attempt_count
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'mfa_code' AND column_name = 'attempt_count') > 0,
  'SELECT 1',
  'ALTER TABLE mfa_code ADD COLUMN attempt_count INT NOT NULL DEFAULT 0'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

-- Widen mfa_code.code for BCrypt hashes
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'mfa_code' AND column_name = 'code' AND character_maximum_length >= 100) > 0,
  'SELECT 1',
  'ALTER TABLE mfa_code MODIFY COLUMN code VARCHAR(100) NOT NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

-- Scrub any stored plaintext display passwords
UPDATE users SET display_password = NULL WHERE display_password IS NOT NULL;
