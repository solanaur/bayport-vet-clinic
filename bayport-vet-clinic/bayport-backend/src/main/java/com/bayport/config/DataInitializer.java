package com.bayport.config;

import com.bayport.entity.*;
import com.bayport.repository.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired private PetRepository petRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private PrescriptionRepository prescriptionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private com.bayport.service.InventoryCatalogService inventoryCatalogService;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private com.bayport.repository.RoleRepository roleRepository;
    @Autowired private com.bayport.security.TemporaryAdminService temporaryAdminService;

    @Value("${bayport.admin.username:}")
    private String bootstrapAdminUsername;
    @Value("${bayport.admin.password:}")
    private String bootstrapAdminPassword;
    @Value("${bayport.admin.email:}")
    private String bootstrapAdminEmail;

    @Autowired private EmailTemplateRepository templateRepo;

    @Autowired private DataSource dataSource;

    @Override
    @Transactional
    public void run(String... args) {

        if (roleRepository.count() == 0) seedRoles();
        if (ownerRepository.count() == 0) seedOwners();
        if (doctorRepository.count() == 0) seedDoctors();
        if (petRepository.count() == 0) seedPetsAndAppointments();
        seedInventoryCatalog();
        ensureFixedAdmin();
        temporaryAdminService.prepareStartup();

        seedEmailTemplates();
        ensureReminderTypeColumn();
    }

    // ---------------------- OWNERS ----------------------
    private void seedOwners() {
        Owner maria = new Owner();
        maria.setFullName("Maria Santos");
        maria.setPhone("0917-111-2222");
        maria.setEmail("maria.santos@example.com");
        maria.setAddress("123 Mabini St., Parañaque");
        ownerRepository.save(maria);

        Owner john = new Owner();
        john.setFullName("John Dela Cruz");
        john.setPhone("0917-555-8888");
        john.setEmail("john.dc@example.com");
        john.setAddress("45 Narra St., Makati");
        ownerRepository.save(john);
    }

    // ---------------------- DOCTORS ----------------------
    private void seedDoctors() {
        Doctor doc = new Doctor();
        doc.setFullName("Dr. Arla Hernando");
        doc.setPhone("0917-333-4444");
        doc.setEmail("arla.hernando@bayportvet.com");
        doc.setSpecialty("Small Animal Medicine");
        doc.setLicenseNo("PRC-998877");
        doc.setHourlyRate(new BigDecimal("1200.00"));
        doctorRepository.save(doc);
    }

    // ---------------------- PETS + APPOINTMENTS ----------------------
    private void seedPetsAndAppointments() {

        Owner maria = ownerRepository.search("Maria").stream().findFirst().orElse(null);
        Owner john = ownerRepository.search("John").stream().findFirst().orElse(null);

        Pet pet1 = new Pet("Choco", "Canine", "Beagle", "Female",
                3, "1234-5678", null, "123 Mabini St.", "N/A");

        if (maria != null) {
            pet1.setOwnerId(maria.getId());
            pet1.setOwner(maria.getFullName());
        }
        petRepository.save(pet1);

        Pet pet2 = new Pet("Mimi", "Feline", "Persian", "Male",
                2, "2233-4455", null, "45 Narra St.", "FCCI");

        if (john != null) {
            pet2.setOwnerId(john.getId());
            pet2.setOwner(john.getFullName());
        }
        petRepository.save(pet2);

        appointmentRepository.save(new Appointment(
            pet1.getId(), pet1.getOwner(),
            LocalDate.now().plusDays(2), "10:00",
            "Dr. Arla Hernando", "Pending"
        ));

        Prescription prescription1 = new Prescription(
            pet1.getId(), pet1.getName(), pet1.getOwner(),
            "Amoxicillin", "250 mg", "Twice daily",
            "Dr. Arla Hernando", LocalDate.now()
        );
        prescriptionRepository.save(prescription1);
    }

    // ---------------------- INVENTORY (POS catalog) ----------------------
    private void seedInventoryCatalog() {
        try {
            inventoryCatalogService.importMissingSkus();
        } catch (Exception e) {
            System.err.println("Inventory catalog import failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ---------------------- ROLES ----------------------
    private void seedRoles() {
        if (roleRepository.findByName("ROLE_ADMIN").isEmpty()) {
            roleRepository.save(new com.bayport.entity.Role("ROLE_ADMIN"));
        }
        if (roleRepository.findByName("ROLE_VET").isEmpty()) {
            roleRepository.save(new com.bayport.entity.Role("ROLE_VET"));
        }
        if (roleRepository.findByName("ROLE_STAFF").isEmpty()) {
            roleRepository.save(new com.bayport.entity.Role("ROLE_STAFF"));
        }
        if (roleRepository.findByName("ROLE_FRONT_OFFICE").isEmpty()) {
            roleRepository.save(new com.bayport.entity.Role("ROLE_FRONT_OFFICE"));
        }
        // Legacy roles kept for existing DB FK rows; new users use ROLE_FRONT_OFFICE only
        if (roleRepository.findByName("ROLE_RECEPTIONIST").isEmpty()) {
            roleRepository.save(new com.bayport.entity.Role("ROLE_RECEPTIONIST"));
        }
        if (roleRepository.findByName("ROLE_PHARMACIST").isEmpty()) {
            roleRepository.save(new com.bayport.entity.Role("ROLE_PHARMACIST"));
        }
    }

    private void ensureFixedAdmin() {
        boolean exists = userRepository.findByUsernameIgnoreCase(com.bayport.security.FixedAdminAccount.USERNAME).isPresent();
        com.bayport.security.FixedAdminAccount.Plan plan =
                com.bayport.security.FixedAdminAccount.plan(exists, bootstrapAdminPassword);
        if (plan != com.bayport.security.FixedAdminAccount.Plan.CREATE) {
            return;
        }
        String password = bootstrapAdminPassword;
        com.bayport.security.PasswordPolicy.require(password);
        if (roleRepository.count() == 0) {
            seedRoles();
        }
        com.bayport.entity.Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseGet(() ->
                roleRepository.save(new com.bayport.entity.Role("ROLE_ADMIN")));
        String username = com.bayport.security.FixedAdminAccount.USERNAME;
        String email = bootstrapAdminEmail == null || bootstrapAdminEmail.isBlank()
                ? username + "@localhost"
                : bootstrapAdminEmail.trim();
        String encoded = passwordEncoder.encode(password);
        User admin = new User("Administrator", "admin", username, encoded);
        admin.setFullName("Administrator");
        admin.setEmail(email);
        admin.setPassword(encoded);
        admin.setPasswordHash(encoded);
        admin.setMustChangePassword(false);
        admin.setMfaEnabled(true);
        admin.setTotpConfirmed(false);
        admin.setActive(true);
        admin.getRoles().add(adminRole);
        userRepository.save(admin);
        System.out.println("[DataInitializer] Created administrator R_Admin. Sign in with the password stored on this computer.");
    }

    // ---------------------- EMAIL TEMPLATES ----------------------
    private void seedEmailTemplates() {

        // Check if templates already exist - if so, only add missing ones
        boolean hasTemplates = templateRepo.count() > 0;

        // --- PET-SPECIFIC REMINDERS ---
        // Helper to save template only if it doesn't exist
        java.util.function.Consumer<EmailTemplate> saveIfMissing = (template) -> {
            if (!hasTemplates || templateRepo.findByCategoryIgnoreCase(template.getCategory()).stream()
                    .noneMatch(t -> t.getSubject().equals(template.getSubject()))) {
                templateRepo.save(template);
            }
        };

        saveIfMissing.accept(new EmailTemplate(null,
                "PET",
                "Vaccine Booster Reminder for {{petName}}",
                "Hello {{ownerName}},\n\nThis is a friendly reminder from Bayport Veterinary Clinic.\n\nYour pet {{petName}} is due for their vaccine booster on {{date}}.\nPlease schedule an appointment at your convenience.\n\nThank you,\nBayport Veterinary Clinic"
        ));

        saveIfMissing.accept(new EmailTemplate(null,
                "PET",
                "{{petName}} is Due for Vaccination",
                "Hello {{ownerName}},\n\nOur records show that {{petName}} is due for their next vaccination.\n\nKeeping vaccinations up to date ensures your pet stays healthy and protected.\n\nPlease book an appointment at your convenience.\n\nWarm regards,\nBayport Veterinary Clinic"
        ));

        saveIfMissing.accept(new EmailTemplate(null,
                "PET",
                "Follow-Up Appointment for {{petName}}",
                "Hello {{ownerName}},\n\nWe hope {{petName}} is doing well!\n\nThis is a reminder for their follow-up appointment on {{date}}.\nContact us if you need to adjust the schedule.\n\nThank you,\nBayport Veterinary Clinic"
        ));

        saveIfMissing.accept(new EmailTemplate(null,
                "PET",
                "Missed Appointment for {{petName}}",
                "Hello {{ownerName}},\n\nWe noticed that the appointment for {{petName}} on {{date}} was missed.\n\nPlease let us know if you would like to reschedule. Your pet's wellbeing is important to us.\n\nBayport Veterinary Clinic"
        ));

        saveIfMissing.accept(new EmailTemplate(null,
                "PET",
                "Annual Checkup Due for {{petName}}",
                "Hello {{ownerName}},\n\nThis is a reminder that {{petName}} is due for their annual wellness checkup.\n\nRegular examinations help us keep your pet healthy and detect any concerns early.\n\nPlease book an appointment at your convenience.\n\nBayport Veterinary Clinic"
        ));

        saveIfMissing.accept(new EmailTemplate(null,
                "PET",
                "Deworming Reminder for {{petName}}",
                "Hello {{ownerName}},\n\nOur records show that {{petName}} is due for their next deworming treatment.\n\nPlease schedule a visit to keep your pet protected.\n\nThank you,\nBayport Veterinary Clinic"
        ));

        saveIfMissing.accept(new EmailTemplate(null,
                "PET",
                "Follow-Up for {{petName}} After Surgery",
                "Hello {{ownerName}},\n\nWe hope {{petName}} is recovering well after the recent procedure.\n\nPlease remember the following:\n• Monitor the incision area\n• Follow the prescribed medications\n• Restrict physical activity\n• Return for a follow-up on {{followUpDate}}\n\nContact us immediately if you notice any unusual symptoms.\n\nBayport Veterinary Clinic"
        ));

        saveIfMissing.accept(new EmailTemplate(null,
                "PET",
                "Test Results Available for {{petName}}",
                "Hello {{ownerName}},\n\nThe laboratory results for {{petName}} are now available.\n\nPlease visit the clinic or contact us to discuss the findings and next steps.\n\nBayport Veterinary Clinic"
        ));

        saveIfMissing.accept(new EmailTemplate(null,
                "PET",
                "Dental Cleaning Reminder for {{petName}}",
                "Hello {{ownerName}},\n\nThis is a reminder that {{petName}} is due for their routine dental cleaning.\n\nRegular dental care helps prevent infections, pain, and tooth loss.\n\nYou may book an appointment anytime.\n\nBayport Veterinary Clinic"
        ));

        saveIfMissing.accept(new EmailTemplate(null,
                "PET",
                "Grooming Reminder for {{petName}}",
                "Hello {{ownerName}},\n\n{{petName}} is due for their grooming session.\n\nRegular grooming helps maintain your pet's comfort and hygiene.\nTo schedule a session, please contact us.\n\nThank you,\nBayport Veterinary Clinic"
        ));

        saveIfMissing.accept(new EmailTemplate(null,
                "PET",
                "Boarding Reservation for {{petName}}",
                "Hello {{ownerName}},\n\nYour boarding reservation for {{petName}} has been confirmed.\n\nDrop-off Date: {{dateStart}}\nPick-up Date: {{dateEnd}}\n\nPlease bring any required items such as food, medication, or comfort blankets.\n\nBayport Veterinary Clinic"
        ));

        saveIfMissing.accept(new EmailTemplate(null,
                "PET",
                "Prescription Ready for {{petName}}",
                "Hello {{ownerName}},\n\nThis is a reminder that the prescription for {{petName}} is ready for pickup.\n\nIf you need any assistance regarding dosage or administration, feel free to consult us.\n\nBayport Veterinary Clinic"
        ));

        // --- GENERAL / BROADCAST REMINDERS ---
        saveIfMissing.accept(new EmailTemplate(null,
                "GENERAL",
                "Important Announcement from Bayport Veterinary Clinic",
                "Hello,\n\nWe have an update regarding our clinic schedule and services.\n\nFor inquiries or appointments, please contact us anytime.\n\nThank you!\nBayport Veterinary Clinic"
        ));

        saveIfMissing.accept(new EmailTemplate(null,
                "GENERAL",
                "Holiday Clinic Schedule",
                "Dear Clients,\n\nPlease be informed that the clinic will be closed on {{date}} for the holiday.\n\nWe will resume normal operations on the next business day.\n\nThank you for your understanding.\nBayport Veterinary Clinic"
        ));

        saveIfMissing.accept(new EmailTemplate(null,
                "GENERAL",
                "Special Offer for Pet Owners!",
                "Hello,\n\nWe're excited to announce a limited-time promo on selected veterinary services.\n\nBook an appointment today and enjoy exclusive benefits.\n\nSincerely,\nBayport Veterinary Clinic"
        ));

        saveIfMissing.accept(new EmailTemplate(null,
                "GENERAL",
                "Clinic Closure",
                "The clinic will be CLOSED on {{date}}. Thank you."
        ));

        saveIfMissing.accept(new EmailTemplate(null,
                "GENERAL",
                "Maintenance Notice",
                "System maintenance will occur on {{date}}. Some services may be unavailable."
        ));

        saveIfMissing.accept(new EmailTemplate(null,
                "GENERAL",
                "Emergency Closure",
                "Due to unforeseen circumstances the clinic is temporarily closed."
        ));

        saveIfMissing.accept(new EmailTemplate(null,
                "GENERAL",
                "Promo Announcement",
                "Good news! New promos and discounts are now available!"
        ));

        saveIfMissing.accept(new EmailTemplate(null,
                "GENERAL",
                "Rabies Awareness Advisory",
                "PUBLIC ADVISORY: Please ensure pets are vaccinated against rabies."
        ));
    }

    /**
     * Older installs created the REMINDER table before the {@code type} column
     * (used for distinguishing PET vs GENERAL reminders) was introduced.
     * <p>
     * When running against those databases (including existing H2 files),
     * Hibernate still generates SQL that selects {@code type}, which causes
     * "Column \"TYPE\" not found" errors like the ones visible in your screenshots.
     * <p>
     * This helper runs once on startup and adds the column if it is missing,
     * using a safe {@code DatabaseMetaData} check so it works for both H2 and
     * MySQL without breaking fresh installs.
     */
    private void ensureReminderTypeColumn() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            // Try both cases - MySQL uses lowercase, H2 uses uppercase
            for (String table : new String[]{"reminder", "REMINDER"}) {
                for (String col : new String[]{"type", "TYPE"}) {
                    try (ResultSet rs = meta.getColumns(null, null, table, col)) {
                        if (rs.next()) return; // Column exists
                    }
                }
            }
            // H2 metadata often uses uppercase table names; MySQL and PostgreSQL use lowercase "reminder".
            String db = meta.getDatabaseProductName() != null ? meta.getDatabaseProductName().toLowerCase() : "";
            String tableName = db.contains("h2") ? "REMINDER" : "reminder";
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN type VARCHAR(20) DEFAULT 'PET' NOT NULL");
                System.out.println("[DataInitializer] Added missing reminder.type column (default 'PET').");
            }
        } catch (Exception ex) {
            System.err.println("[DataInitializer] Unable to ensure reminder.type column exists: " + ex.getMessage());
        }
    }
}
