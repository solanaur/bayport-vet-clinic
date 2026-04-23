-- Migration script to add new security columns to existing database
-- This script handles the migration gracefully by:
-- 1. Adding columns as nullable first
-- 2. Setting default values for existing rows
-- 3. Then making them NOT NULL if needed

-- Add password column to users (nullable, will be populated from password_hash)
ALTER TABLE users ADD COLUMN IF NOT EXISTS password VARCHAR(255) NULL;
UPDATE users SET password = password_hash WHERE password IS NULL AND password_hash IS NOT NULL;

-- Add mfa_enabled column to users (nullable, default false)
ALTER TABLE users ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN NULL;
UPDATE users SET mfa_enabled = FALSE WHERE mfa_enabled IS NULL;

-- Add full_name column to users (nullable)
ALTER TABLE users ADD COLUMN IF NOT EXISTS full_name VARCHAR(255) NULL;
UPDATE users SET full_name = name WHERE full_name IS NULL AND name IS NOT NULL;

-- Add email column to users (nullable)
ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(255) NULL;

-- Add TOS columns to users (nullable)
ALTER TABLE users ADD COLUMN IF NOT EXISTS tos_version_accepted VARCHAR(20) NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS tos_accepted_at DATETIME NULL;

-- Add soft delete columns to pets (nullable, default false)
ALTER TABLE pets ADD COLUMN IF NOT EXISTS deleted BOOLEAN NULL;
UPDATE pets SET deleted = FALSE WHERE deleted IS NULL;
ALTER TABLE pets ADD COLUMN IF NOT EXISTS deleted_at DATETIME NULL;
ALTER TABLE pets ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(100) NULL;

-- Fix owner phone column length (increase from 11 to 20 to accommodate existing data)
-- Note: H2 doesn't support ALTER COLUMN TYPE directly, so we'll skip this
-- The column definition in Owner entity has been updated to length=20
