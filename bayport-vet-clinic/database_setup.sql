-- Bayport Veterinary Clinic - MySQL Database Setup
-- Run this script in MySQL Workbench to create the database
-- Then start the Bayport backend - Hibernate will create/update tables automatically

CREATE DATABASE IF NOT EXISTS bayport_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE bayport_db;

-- Note: Tables are created automatically by Hibernate (spring.jpa.hibernate.ddl-auto=update)
-- This script only creates the database. Default users and sample data are seeded by DataInitializer on first run.
--
-- Default login credentials after first run (seeded by DataInitializer):
-- | Username   | Password       | Role                          |
-- |------------|----------------|-------------------------------|
-- | admin      | admin123       | Admin                         |
-- | vet        | vet123         | Veterinarian                  |
-- | frontdesk  | frontdesk123   | Front Office (recept + pharm) |
-- Legacy usernames recept / pharm (if present) are migrated to Front Office on startup.
