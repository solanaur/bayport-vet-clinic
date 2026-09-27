-- Consultation is the parent visit; services (procedures) and invoice lines
-- keep itemized detail. Invoice total is a derived SUM of service_cost.

CREATE TABLE IF NOT EXISTS consultations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    pet_id BIGINT NOT NULL,
    appointment_id BIGINT NULL,
    consult_date DATE NOT NULL,
    consult_time VARCHAR(16) NULL,
    diagnosis VARCHAR(500) NULL,
    notes VARCHAR(4000) NULL,
    vet VARCHAR(120) NULL,
    created_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_consultations_pet (pet_id),
    KEY idx_consultations_date (consult_date)
);

ALTER TABLE procedures ADD COLUMN IF NOT EXISTS consultation_id BIGINT NULL;
ALTER TABLE billing_records ADD COLUMN IF NOT EXISTS consultation_id BIGINT NULL;

CREATE TABLE IF NOT EXISTS billing_lines (
    id BIGINT NOT NULL AUTO_INCREMENT,
    billing_record_id BIGINT NOT NULL,
    consultation_id BIGINT NULL,
    procedure_id BIGINT NULL,
    service_name VARCHAR(255) NOT NULL,
    service_cost DECIMAL(14, 2) NOT NULL,
    performed_by VARCHAR(120) NULL,
    line_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_billing_lines_invoice (billing_record_id),
    KEY idx_billing_lines_consultation (consultation_id),
    CONSTRAINT fk_billing_lines_record FOREIGN KEY (billing_record_id) REFERENCES billing_records (id)
);
