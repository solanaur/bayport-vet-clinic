-- Empty clinic MySQL database. Matches the schema after Flyway V13.
-- flyway-baseline-version: 13
-- Applied only when the users table does not exist. Does not insert staff or clinical rows.

CREATE TABLE IF NOT EXISTS roles (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_roles_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(255) NOT NULL,
  password VARCHAR(255) NULL,
  full_name VARCHAR(255) NULL,
  email VARCHAR(255) NULL,
  mfa_enabled BIT(1) NULL,
  totp_secret VARCHAR(512) NULL,
  totp_last_step BIGINT NULL,
  token_version INT NULL DEFAULT 0,
  totp_prompted BIT(1) NULL,
  totp_confirmed BIT(1) NULL,
  phone VARCHAR(32) NULL,
  must_change_password BIT(1) NULL,
  tos_version_accepted VARCHAR(255) NULL,
  tos_accepted_at DATETIME(6) NULL,
  active BIT(1) NOT NULL,
  name VARCHAR(255) NOT NULL,
  role VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_roles (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS owners (
  id BIGINT NOT NULL AUTO_INCREMENT,
  full_name VARCHAR(255) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  email VARCHAR(255) NULL,
  address VARCHAR(255) NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pets (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  species VARCHAR(255) NULL,
  breed VARCHAR(255) NULL,
  gender VARCHAR(255) NULL,
  age INT NULL,
  age_display VARCHAR(64) NULL,
  microchip VARCHAR(255) NULL,
  color VARCHAR(255) NULL,
  weight VARCHAR(255) NULL,
  date_of_birth DATE NULL,
  owner_name VARCHAR(255) NULL,
  owner_id BIGINT NULL,
  address VARCHAR(255) NULL,
  federation VARCHAR(255) NULL,
  photo VARCHAR(255) NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  deleted BIT(1) NULL,
  deleted_at DATETIME(6) NULL,
  deleted_by VARCHAR(100) NULL,
  last_vaccination_date DATE NULL,
  last_vaccination_place VARCHAR(255) NULL,
  last_vaccination_vet VARCHAR(255) NULL,
  allergies VARCHAR(500) NULL,
  chronic_conditions VARCHAR(500) NULL,
  known_medications TEXT NULL,
  assigned_veterinarian_id BIGINT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS doctors (
  id BIGINT NOT NULL AUTO_INCREMENT,
  full_name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NULL,
  phone VARCHAR(255) NULL,
  specialty VARCHAR(255) NULL,
  license_no VARCHAR(255) NULL,
  hourly_rate DECIMAL(12, 2) NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS appointments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  pet_id BIGINT NULL,
  owner VARCHAR(255) NULL,
  code VARCHAR(255) NULL,
  `date` DATE NULL,
  time VARCHAR(255) NULL,
  vet VARCHAR(255) NULL,
  status VARCHAR(255) NULL,
  completed_at DATE NULL,
  consultation_started_at DATETIME(6) NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS procedures (
  id BIGINT NOT NULL AUTO_INCREMENT,
  procedure_date DATE NULL,
  procedure_name VARCHAR(255) NULL,
  notes VARCHAR(255) NULL,
  vet VARCHAR(255) NULL,
  category VARCHAR(255) NULL,
  lab_type VARCHAR(255) NULL,
  medications VARCHAR(255) NULL,
  cost DECIMAL(12, 2) NULL,
  consultation_id BIGINT NULL,
  pet_id BIGINT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS medications (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  description VARCHAR(255) NULL,
  dosage VARCHAR(255) NULL,
  unit VARCHAR(255) NULL,
  unit_price DECIMAL(12, 2) NULL,
  active BIT(1) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS procedure_medications (
  id BIGINT NOT NULL AUTO_INCREMENT,
  procedure_id BIGINT NOT NULL,
  medication_id BIGINT NOT NULL,
  dosage_override VARCHAR(255) NULL,
  instructions VARCHAR(255) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_procedure_medications_procedure FOREIGN KEY (procedure_id) REFERENCES procedures (id),
  CONSTRAINT fk_procedure_medications_medication FOREIGN KEY (medication_id) REFERENCES medications (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS prescriptions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  pet_id BIGINT NULL,
  consultation_procedure_id BIGINT NULL,
  pet VARCHAR(255) NULL,
  owner VARCHAR(255) NULL,
  drug VARCHAR(255) NULL,
  dosage VARCHAR(255) NULL,
  directions VARCHAR(255) NULL,
  notes VARCHAR(2000) NULL,
  prescriber VARCHAR(255) NULL,
  `date` DATE NULL,
  dispensed BIT(1) NOT NULL,
  dispensed_at DATE NULL,
  price DECIMAL(12, 2) NULL,
  archived BIT(1) NOT NULL,
  dispense_qty INT NULL,
  diagnosis VARCHAR(255) NULL,
  days_supply VARCHAR(255) NULL,
  rx_status VARCHAR(255) NULL,
  vet_notes VARCHAR(2000) NULL,
  prescriber_license_no VARCHAR(255) NULL,
  printed_at DATETIME(6) NULL,
  print_count INT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS billing_records (
  id BIGINT NOT NULL AUTO_INCREMENT,
  pet_id BIGINT NULL,
  owner_id BIGINT NULL,
  owner_name VARCHAR(255) NULL,
  pet_name VARCHAR(255) NULL,
  description VARCHAR(255) NULL,
  amount DECIMAL(12, 2) NULL,
  subtotal_amount DECIMAL(12, 2) NULL,
  discount_amount DECIMAL(12, 2) NULL,
  status VARCHAR(255) NULL,
  reference_type VARCHAR(255) NULL,
  reference_id BIGINT NULL,
  consultation_id BIGINT NULL,
  issued_at DATETIME(6) NULL,
  paid_at DATETIME(6) NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS billing_lines (
  id BIGINT NOT NULL AUTO_INCREMENT,
  billing_record_id BIGINT NOT NULL,
  consultation_id BIGINT NULL,
  procedure_id BIGINT NULL,
  service_name VARCHAR(255) NOT NULL,
  service_cost DECIMAL(14, 2) NOT NULL,
  performed_by VARCHAR(120) NULL,
  line_order INT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_billing_lines_record FOREIGN KEY (billing_record_id) REFERENCES billing_records (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sales (
  id BIGINT NOT NULL AUTO_INCREMENT,
  billing_id BIGINT NULL,
  pet_id BIGINT NULL,
  pet_name VARCHAR(255) NULL,
  source VARCHAR(255) NULL,
  note TEXT NULL,
  amount DECIMAL(12, 2) NULL,
  occurred_at DATETIME(6) NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sale_lines (
  id BIGINT NOT NULL AUTO_INCREMENT,
  sale_id BIGINT NOT NULL,
  line_kind VARCHAR(20) NOT NULL,
  item_name VARCHAR(512) NOT NULL,
  sku VARCHAR(64) NULL,
  quantity INT NOT NULL,
  unit_price DECIMAL(12, 2) NULL,
  line_total DECIMAL(12, 2) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_sale_lines_sale FOREIGN KEY (sale_id) REFERENCES sales (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory_items (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  sku VARCHAR(255) NOT NULL,
  category VARCHAR(255) NULL,
  unit VARCHAR(255) NULL,
  quantity INT NULL,
  reorder_level INT NULL,
  photo VARCHAR(255) NULL,
  unit_price DECIMAL(12, 2) NULL,
  last_updated DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_inventory_sku (sku)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory_sku_suppression (
  sku VARCHAR(128) NOT NULL,
  created_at DATETIME(6) NULL,
  PRIMARY KEY (sku)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reminder (
  id BIGINT NOT NULL AUTO_INCREMENT,
  pet_id BIGINT NULL,
  owner_id BIGINT NULL,
  type VARCHAR(255) NOT NULL,
  `date` DATE NULL,
  message VARCHAR(255) NULL,
  sent BIT(1) NOT NULL,
  sent_at DATETIME(6) NULL,
  target_email VARCHAR(255) NULL,
  auto_generated BIT(1) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pet_medical_records (
  id BIGINT NOT NULL AUTO_INCREMENT,
  pet_id BIGINT NOT NULL,
  record_type VARCHAR(50) NOT NULL,
  record_date DATE NULL,
  title VARCHAR(255) NULL,
  description VARCHAR(255) NULL,
  source_clinic VARCHAR(255) NULL,
  veterinarian VARCHAR(255) NULL,
  vaccine_type VARCHAR(255) NULL,
  dose_number VARCHAR(255) NULL,
  next_due_date DATE NULL,
  diagnosis VARCHAR(255) NULL,
  treatment_plan VARCHAR(255) NULL,
  test_results VARCHAR(255) NULL,
  medications VARCHAR(255) NULL,
  attachment_url VARCHAR(255) NULL,
  attachment_name VARCHAR(255) NULL,
  external_record BIT(1) NOT NULL,
  created_at DATETIME(6) NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  message VARCHAR(500) NOT NULL,
  type VARCHAR(50) NOT NULL,
  is_read BIT(1) NOT NULL,
  read_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mfa_code (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NULL,
  email VARCHAR(255) NULL,
  code VARCHAR(100) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  used BIT(1) NOT NULL,
  attempt_count INT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_mfa_code_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mfa_recovery_codes (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  code_hash VARCHAR(100) NOT NULL,
  used_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_mfa_recovery_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS login_lockouts (
  id BIGINT NOT NULL AUTO_INCREMENT,
  lock_key VARCHAR(200) NOT NULL,
  failure_count INT NOT NULL,
  locked_until_epoch BIGINT NOT NULL,
  next_allowed_epoch BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_login_lockouts_key (lock_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS auth_rate_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  bucket_key VARCHAR(200) NOT NULL,
  event_epoch BIGINT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_auth_rate_bucket (bucket_key, event_epoch)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS email_template (
  id BIGINT NOT NULL AUTO_INCREMENT,
  category VARCHAR(255) NULL,
  subject VARCHAR(255) NULL,
  body TEXT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS print_jobs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  branch_code VARCHAR(100) NOT NULL,
  device_name VARCHAR(255) NULL,
  status VARCHAR(32) NOT NULL,
  receipt_html TEXT NOT NULL,
  attempt_count INT NOT NULL,
  last_error TEXT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS operation_logs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  `timestamp` DATETIME(6) NULL,
  operation_type VARCHAR(255) NULL,
  message VARCHAR(255) NULL,
  pet_id BIGINT NULL,
  username VARCHAR(100) NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  `timestamp` DATETIME(6) NOT NULL,
  username VARCHAR(255) NULL,
  user_id BIGINT NULL,
  action VARCHAR(50) NOT NULL,
  entity_name VARCHAR(100) NOT NULL,
  entity_id VARCHAR(100) NOT NULL,
  details TEXT NULL,
  ip_address VARCHAR(45) NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS data_access_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  `timestamp` DATETIME(6) NOT NULL,
  username VARCHAR(255) NULL,
  user_id BIGINT NULL,
  entity_name VARCHAR(100) NOT NULL,
  entity_id VARCHAR(100) NOT NULL,
  operation VARCHAR(50) NOT NULL,
  ip_address VARCHAR(45) NULL,
  details TEXT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
