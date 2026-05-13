-- When a consultation is active (approved), elapsed time is computed from this timestamp.
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS consultation_started_at DATETIME(6) NULL;
