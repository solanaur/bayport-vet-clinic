package com.bayport.auth;

import com.bayport.entity.MfaCode;
import com.bayport.entity.User;
import com.bayport.repository.MfaCodeRepository;
import com.bayport.service.EmailService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
public class MfaService {
    private final MfaCodeRepository mfaCodeRepository;
    private final EmailService emailService;

    public MfaService(MfaCodeRepository mfaCodeRepository, EmailService emailService) {
        this.mfaCodeRepository = mfaCodeRepository;
        this.emailService = emailService;
    }

    public void sendMfaCode(User user) {
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            throw new RuntimeException("User email is required for MFA");
        }

        String code = String.valueOf(1000 + new Random().nextInt(9000)); // 4-digit code
        
        MfaCode mfa = new MfaCode();
        mfa.setUser(user);
        mfa.setCode(code);
        mfa.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        mfa.setUsed(false);
        
        mfaCodeRepository.save(mfa);
        
        try {
            emailService.send(
                user.getEmail(),
                "Your Verification Code",
                "Your verification code is: " + code + "\n\nThis code will expire in 5 minutes."
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to send MFA code: " + e.getMessage(), e);
        }
    }

    public boolean verifyCode(User user, String code) {
        Optional<MfaCode> opt = mfaCodeRepository.findTopByUserAndCodeAndUsedFalseOrderByExpiresAtDesc(user, code);
        
        if (opt.isEmpty()) {
            return false;
        }
        
        MfaCode mfa = opt.get();
        
        if (mfa.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        
        mfa.setUsed(true);
        mfaCodeRepository.save(mfa);
        
        return true;
    }

    // Send OTP by email only (for user creation before user exists)
    public void sendOtpByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new RuntimeException("Email is required");
        }

        String code = String.valueOf(1000 + new Random().nextInt(9000)); // 4-digit code
        
        MfaCode mfa = new MfaCode();
        mfa.setUser(null); // No user yet
        mfa.setEmail(email); // Store email directly
        mfa.setCode(code);
        mfa.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        mfa.setUsed(false);
        
        mfaCodeRepository.save(mfa);
        
        try {
            String termsAndConditions = getTermsAndConditions();
            String emailBody = "Your verification code is: " + code + "\n\nThis code will expire in 5 minutes.\n\nUse this code to complete your account creation.\n\n" +
                    "================================================================================\n\n" +
                    termsAndConditions;
            
            emailService.send(
                email,
                "Your Verification Code for Account Creation",
                emailBody
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to send OTP: " + e.getMessage(), e);
        }
    }
    
    private String getTermsAndConditions() {
        return "Terms and Conditions for System Use\n\n" +
                "Bayport Veterinary Clinic – Veterinary Clinic Management System (VCMS)\n\n" +
                "Effective Date: [Insert Date]\n" +
                "Version: v1.0\n\n" +
                "1. Acceptance of Terms\n\n" +
                "By creating an account and accessing the Bayport Veterinary Clinic's Veterinary Clinic Management System (VCMS), you acknowledge that you have read, understood, and agreed to comply with these Terms and Conditions.\n" +
                "If you do not agree, you must not proceed with the account registration or system use.\n\n" +
                "2. Authorized Use Only\n\n" +
                "The VCMS is intended solely for authorized personnel of Bayport Veterinary Clinic (e.g., Admin, Veterinarian, Staff).\n" +
                "You agree to:\n" +
                "- Use the system only for legitimate clinic-related activities\n" +
                "- Avoid unauthorized access, modification, or misuse of clinic data\n" +
                "- Follow all system usage policies provided by Bayport administrators\n" +
                "Unauthorized access or misuse may result in account suspension or legal action.\n\n" +
                "3. Accuracy of Information\n\n" +
                "Users are responsible for ensuring all information entered into the system—including pet records, client details, treatments, billing, and appointments—is accurate and truthful.\n" +
                "Providing false or misleading information may result in restricted access or account termination.\n\n" +
                "4. Account Security and Login Verification\n\n" +
                "To protect sensitive data within Bayport Veterinary Clinic:\n" +
                "- You must keep your password and login credentials confidential.\n" +
                "- Sharing accounts is strictly prohibited.\n" +
                "- The system uses Multi-Factor Authentication (MFA) for security. This includes:\n" +
                "  * OTP verification during new account creation\n" +
                "  * OTP verification every time a user logs in\n" +
                "- Users must enter the OTP sent to their registered email to successfully access their account.\n" +
                "- Suspicious login behavior may result in temporary account lockout.\n\n" +
                "5. Data Privacy and Confidentiality\n\n" +
                "By using the Bayport VCMS, you agree to protect and maintain the confidentiality of:\n" +
                "- Pet medical records\n" +
                "- Client personal and contact information\n" +
                "- Treatment, appointment, and billing information\n" +
                "You must not share or distribute any confidential data outside of authorized clinic operations.\n" +
                "All access to data is monitored and logged for security purposes.\n\n" +
                "6. Activity Monitoring and Audit Logs\n\n" +
                "For accountability and transparency, the system automatically records:\n" +
                "- User activities (create, update, delete actions)\n" +
                "- Data access logs (when a user views or retrieves information)\n" +
                "- Login attempts and MFA verification events\n" +
                "Bayport administrators may review these logs at any time for auditing or security investigations.\n\n" +
                "7. Proper Use of System Features\n\n" +
                "Users agree to use system features responsibly, including:\n" +
                "- Creating or updating client and pet records\n" +
                "- Managing veterinary appointments\n" +
                "- Modifying treatments, vaccinations, and medical details\n" +
                "- Sending communication such as reminders or notifications\n" +
                "Users must not disrupt or manipulate the system in any unauthorized way.\n\n" +
                "8. Prohibited Actions\n\n" +
                "You are strictly prohibited from:\n" +
                "- Bypassing or attempting to disable security features\n" +
                "- Accessing modules or data outside your assigned role\n" +
                "- Unauthorized deletion or alteration of records\n" +
                "- Installing or executing unapproved software or scripts within the system\n" +
                "Any violation may result in immediate account suspension or removal.\n\n" +
                "9. System Availability and Backup\n\n" +
                "The Bayport VCMS operates in both:\n" +
                "- Offline mode (local desktop use for daily operations), and\n" +
                "- Online mode (for email notifications and cloud backup functionality)\n" +
                "To avoid data loss, administrators must ensure regular backups.\n" +
                "Bayport Veterinary Clinic is not responsible for data loss caused by:\n" +
                "- Hardware or device failure\n" +
                "- Network interruptions\n" +
                "- Unauthorized tampering\n\n" +
                "10. Updates to Terms and Conditions\n\n" +
                "Bayport Veterinary Clinic may revise these Terms at any time.\n" +
                "If updates are made:\n" +
                "- Users will be shown the updated Terms during their next login\n" +
                "- Users must accept the new Terms to continue using the system\n" +
                "- Failure to accept new Terms will restrict system access.\n\n" +
                "11. User Responsibilities\n\n" +
                "By accepting these Terms, you confirm that:\n" +
                "- You understand your assigned system role and permissions\n" +
                "- You are responsible for any actions performed under your account\n" +
                "- You will report technical issues or suspicious activity to Bayport admins immediately\n\n" +
                "12. Acceptance\n\n" +
                "By creating an account or continuing to use the Bayport Veterinary Clinic Management System, you acknowledge that you fully accept and agree to follow these Terms and Conditions.";
    }

    // Verify OTP by email only (for user creation)
    public boolean verifyOtpByEmail(String email, String code) {
        Optional<MfaCode> opt = mfaCodeRepository.findTopByEmailAndCodeAndUsedFalseOrderByExpiresAtDesc(email, code);
        
        if (opt.isEmpty()) {
            return false;
        }
        
        MfaCode mfa = opt.get();
        
        if (mfa.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        
        mfa.setUsed(true);
        mfaCodeRepository.save(mfa);
        
        return true;
    }
}
