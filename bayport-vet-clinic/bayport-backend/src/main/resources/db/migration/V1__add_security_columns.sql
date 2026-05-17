-- Security columns on users/pets. MySQL 9 does not support `ADD COLUMN IF NOT EXISTS`; use metadata + prepared statements.
SET @db := DATABASE();

-- users.password
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'users' AND column_name = 'password') > 0,
  'SELECT 1',
  'ALTER TABLE users ADD COLUMN password VARCHAR(255) NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
UPDATE users SET password = password_hash WHERE password IS NULL AND password_hash IS NOT NULL;

-- users.mfa_enabled
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'users' AND column_name = 'mfa_enabled') > 0,
  'SELECT 1',
  'ALTER TABLE users ADD COLUMN mfa_enabled BOOLEAN NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
UPDATE users SET mfa_enabled = FALSE WHERE mfa_enabled IS NULL;

-- users.full_name
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'users' AND column_name = 'full_name') > 0,
  'SELECT 1',
  'ALTER TABLE users ADD COLUMN full_name VARCHAR(255) NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
UPDATE users SET full_name = name WHERE full_name IS NULL AND name IS NOT NULL;

-- users.email
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'users' AND column_name = 'email') > 0,
  'SELECT 1',
  'ALTER TABLE users ADD COLUMN email VARCHAR(255) NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

-- users.tos_version_accepted
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'users' AND column_name = 'tos_version_accepted') > 0,
  'SELECT 1',
  'ALTER TABLE users ADD COLUMN tos_version_accepted VARCHAR(20) NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

-- users.tos_accepted_at
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'users' AND column_name = 'tos_accepted_at') > 0,
  'SELECT 1',
  'ALTER TABLE users ADD COLUMN tos_accepted_at DATETIME NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

-- pets.deleted
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'pets' AND column_name = 'deleted') > 0,
  'SELECT 1',
  'ALTER TABLE pets ADD COLUMN deleted BOOLEAN NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
UPDATE pets SET deleted = FALSE WHERE deleted IS NULL;

-- pets.deleted_at
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'pets' AND column_name = 'deleted_at') > 0,
  'SELECT 1',
  'ALTER TABLE pets ADD COLUMN deleted_at DATETIME NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;

-- pets.deleted_by
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'pets' AND column_name = 'deleted_by') > 0,
  'SELECT 1',
  'ALTER TABLE pets ADD COLUMN deleted_by VARCHAR(100) NULL'
);
PREPARE _bp_stmt FROM @sql; EXECUTE _bp_stmt; DEALLOCATE PREPARE _bp_stmt;
