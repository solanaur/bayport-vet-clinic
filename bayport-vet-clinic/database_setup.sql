-- Creates the empty clinic database only.
-- The production app creates tables with Flyway. See docs/DEPLOYMENT.md.
-- Create the MySQL user yourself. Do not put a password in this file.
-- The application user should be bayport_app@127.0.0.1 with rights on bayport_db only.

CREATE DATABASE IF NOT EXISTS bayport_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
