-- Clinic backup history. Does not change clinical rows.
CREATE TABLE IF NOT EXISTS backup_runs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  started_at DATETIME(6) NOT NULL,
  finished_at DATETIME(6) NULL,
  status VARCHAR(20) NOT NULL,
  location VARCHAR(500) NULL,
  size_bytes BIGINT NULL,
  sha256 VARCHAR(64) NULL,
  message VARCHAR(500) NULL,
  verify_status VARCHAR(20) NULL,
  PRIMARY KEY (id)
);
