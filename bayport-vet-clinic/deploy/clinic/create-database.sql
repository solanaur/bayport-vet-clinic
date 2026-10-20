# Creates the empty clinic database. Does not create a password or a staff account.
# Run in MySQL as an administrator, then create bayport_app@127.0.0.1 yourself
# with a password of at least 12 characters. Grant that user rights on bayport_db only.

CREATE DATABASE IF NOT EXISTS bayport_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
