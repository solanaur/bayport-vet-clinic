-- One-time data fix: legacy default vet "Dr. Andrea Cruz" / "Dr. Cruz" -> "Dr. Arla Hernando" everywhere the app stores display names.

UPDATE users
SET name = 'Dr. Arla Hernando',
    full_name = 'Dr. Arla Hernando'
WHERE username = 'vet'
   OR TRIM(COALESCE(name, '')) IN ('Dr. Andrea Cruz', 'Andrea Cruz', 'Dr Andrea Cruz', 'Dr. Cruz', 'Doctor Andrea Cruz')
   OR TRIM(COALESCE(full_name, '')) IN ('Dr. Andrea Cruz', 'Andrea Cruz', 'Dr Andrea Cruz', 'Dr. Cruz', 'Doctor Andrea Cruz');

UPDATE appointments
SET vet = 'Dr. Arla Hernando'
WHERE TRIM(vet) IN ('Dr. Andrea Cruz', 'Andrea Cruz', 'Dr Andrea Cruz', 'Dr. Cruz', 'Doctor Andrea Cruz');

UPDATE prescriptions
SET prescriber = 'Dr. Arla Hernando'
WHERE TRIM(prescriber) IN ('Dr. Andrea Cruz', 'Andrea Cruz', 'Dr Andrea Cruz', 'Dr. Cruz', 'Doctor Andrea Cruz');

UPDATE procedures
SET vet = 'Dr. Arla Hernando'
WHERE TRIM(vet) IN ('Dr. Andrea Cruz', 'Andrea Cruz', 'Dr Andrea Cruz', 'Dr. Cruz', 'Doctor Andrea Cruz');

UPDATE doctors
SET full_name = 'Dr. Arla Hernando',
    email = 'arla.hernando@bayportvet.com'
WHERE TRIM(full_name) IN ('Dr. Andrea Cruz', 'Andrea Cruz', 'Dr Andrea Cruz', 'Dr. Cruz', 'Doctor Andrea Cruz')
   OR full_name LIKE '%Andrea Cruz%';

UPDATE notifications
SET message = REPLACE(REPLACE(message, 'Dr. Andrea Cruz', 'Dr. Arla Hernando'), 'Dr. Cruz', 'Dr. Arla Hernando')
WHERE message LIKE '%Dr. Andrea Cruz%' OR message LIKE '%Dr. Cruz%' OR message LIKE '%Andrea Cruz%';

UPDATE audit_log
SET details = REPLACE(REPLACE(COALESCE(details, ''), 'Dr. Andrea Cruz', 'Dr. Arla Hernando'), 'Dr. Cruz', 'Dr. Arla Hernando')
WHERE details LIKE '%Dr. Andrea Cruz%' OR details LIKE '%Dr. Cruz%' OR details LIKE '%Andrea Cruz%';
