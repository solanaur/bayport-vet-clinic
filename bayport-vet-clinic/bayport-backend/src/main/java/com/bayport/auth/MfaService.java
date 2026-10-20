package com.bayport.auth;

import com.bayport.entity.MfaCode;
import com.bayport.entity.User;
import com.bayport.repository.MfaCodeRepository;
import com.bayport.security.RateLimitService;
import com.bayport.service.EmailService;
import com.bayport.service.NotificationService;
import com.bayport.service.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Service
public class MfaService {
    private static final Logger log = LoggerFactory.getLogger(MfaService.class);

    public record DeliveryResult(boolean emailDelivered, boolean smsDelivered) {}

    private final MfaCodeRepository mfaCodeRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final RateLimitService rateLimitService;
    private final SmsService smsService;
    private final int maxVerifyAttempts;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Executor otpEmailExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "otp-email");
        t.setDaemon(true);
        return t;
    });

    public MfaService(
            MfaCodeRepository mfaCodeRepository,
            EmailService emailService,
            NotificationService notificationService,
            PasswordEncoder passwordEncoder,
            RateLimitService rateLimitService,
            SmsService smsService,
            @Value("${bayport.security.otp.max-verify-attempts:5}") int maxVerifyAttempts) {
        this.mfaCodeRepository = mfaCodeRepository;
        this.emailService = emailService;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
        this.rateLimitService = rateLimitService;
        this.smsService = smsService;
        this.maxVerifyAttempts = Math.max(3, maxVerifyAttempts);
    }

    public DeliveryResult sendMfaCode(User user) {
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            throw new RuntimeException("User email is required for MFA");
        }
        String rateKey = user.getUsername() != null ? user.getUsername() : user.getEmail();
        if (!rateLimitService.allowOtpSend(rateKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many OTP requests. Please try again later.");
        }

        String code = generateSixDigitCode();
        invalidateOpenCodesForUser(user);

        MfaCode mfa = new MfaCode();
        mfa.setUser(user);
        mfa.setEmail(user.getEmail());
        mfa.setCode(passwordEncoder.encode(code));
        mfa.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        mfa.setUsed(false);
        mfa.setAttemptCount(0);
        mfaCodeRepository.save(mfa);

        boolean emailQueued = queueLoginOtpEmail(user.getEmail(), user.getUsername(), code);
        boolean smsQueued = queueOtpSms(user.getPhone(), code);
        return new DeliveryResult(emailQueued, smsQueued);
    }

    public boolean verifyCode(User user, String code) {
        String rateKey = user.getUsername() != null ? user.getUsername() : String.valueOf(user.getId());
        if (!rateLimitService.allowOtpVerify(rateKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many verification attempts. Please try again later.");
        }
        String normalized = code == null ? "" : code.trim();
        if (normalized.isEmpty() || user.getId() == null) {
            return false;
        }

        List<MfaCode> open = mfaCodeRepository.findOpenForUser(user.getId(), LocalDateTime.now());
        if (open.isEmpty()) {
            return false;
        }

        MfaCode mfa = open.get(0);
        if (mfa.getAttemptCount() >= maxVerifyAttempts) {
            mfa.setUsed(true);
            mfaCodeRepository.save(mfa);
            return false;
        }

        boolean matches = matchesCode(mfa.getCode(), normalized);
        if (!matches) {
            mfa.setAttemptCount(mfa.getAttemptCount() + 1);
            if (mfa.getAttemptCount() >= maxVerifyAttempts) {
                mfa.setUsed(true);
            }
            mfaCodeRepository.save(mfa);
            return false;
        }

        mfa.setUsed(true);
        mfaCodeRepository.save(mfa);
        return true;
    }

    public DeliveryResult sendOtpByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new RuntimeException("Email is required");
        }
        if (!rateLimitService.allowOtpSend(email)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many OTP requests. Please try again later.");
        }

        String code = generateSixDigitCode();
        invalidateOpenCodesForEmail(email.trim());

        MfaCode mfa = new MfaCode();
        mfa.setUser(null);
        mfa.setEmail(email.trim());
        mfa.setCode(passwordEncoder.encode(code));
        mfa.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        mfa.setUsed(false);
        mfa.setAttemptCount(0);
        mfaCodeRepository.save(mfa);

        boolean emailQueued = queueCreateAccountOtpEmail(email.trim(), code);
        return new DeliveryResult(emailQueued, false);
    }

    public boolean verifyOtpByEmail(String email, String code) {
        if (!rateLimitService.allowOtpVerify(email)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many verification attempts. Please try again later.");
        }
        String normalized = code == null ? "" : code.trim();
        if (normalized.isEmpty() || email == null || email.trim().isEmpty()) {
            return false;
        }

        List<MfaCode> open = mfaCodeRepository.findOpenForEmail(email.trim(), LocalDateTime.now());
        if (open.isEmpty()) {
            return false;
        }

        MfaCode mfa = open.get(0);
        if (mfa.getAttemptCount() >= maxVerifyAttempts) {
            mfa.setUsed(true);
            mfaCodeRepository.save(mfa);
            return false;
        }

        boolean matches = matchesCode(mfa.getCode(), normalized);
        if (!matches) {
            mfa.setAttemptCount(mfa.getAttemptCount() + 1);
            if (mfa.getAttemptCount() >= maxVerifyAttempts) {
                mfa.setUsed(true);
            }
            mfaCodeRepository.save(mfa);
            return false;
        }

        mfa.setUsed(true);
        mfaCodeRepository.save(mfa);
        return true;
    }

    /**
     * Queue Gmail/SMTP after the OTP is already persisted so the HTTP response is not blocked.
     * Slow or failed delivery must not fail create-user or login. The code is never returned to the caller.
     */
    private boolean queueCreateAccountOtpEmail(String email, String code) {
        if (!emailService.isConfigured()) {
            notifySignupOtp(email);
            return false;
        }
        otpEmailExecutor.execute(() -> {
            try {
                String termsAndConditions = getTermsAndConditions();
                String emailBody = "Your verification code is: " + code + "\n\nThis code will expire in 5 minutes.\n\nUse this code to complete your account creation.\n\n" +
                        "================================================================================\n\n" +
                        termsAndConditions;
                emailService.send(email, "Your Verification Code for Account Creation", emailBody);
                log.info("Create-account OTP email sent to {}", email);
            } catch (Exception e) {
                log.warn("Create-account OTP email failed for {}: {}", email, e.getMessage());
                notifySignupOtp(email);
            }
        });
        return true;
    }

    private boolean queueOtpSms(String phone, String code) {
        if (phone == null || phone.isBlank() || !smsService.isConfigured()) {
            return false;
        }
        otpEmailExecutor.execute(() -> {
            SmsService.SmsResult result = smsService.sendVerificationCode(phone, code);
            if (!result.accepted()) {
                log.warn("OTP SMS was not accepted: {}", result.detail());
            }
        });
        return true;
    }

    private boolean queueLoginOtpEmail(String email, String username, String code) {
        if (!emailService.isConfigured()) {
            notifyLoginOtp(username);
            return false;
        }
        otpEmailExecutor.execute(() -> {
            try {
                emailService.send(
                        email,
                        "Your Verification Code",
                        "Your verification code is: " + code + "\n\nThis code will expire in 5 minutes."
                );
                log.info("Login OTP email sent to {}", email);
            } catch (Exception e) {
                log.warn("OTP email failed for {}: {}", username, e.getMessage());
                notifyLoginOtp(username);
            }
        });
        return true;
    }

    private void notifySignupOtp(String email) {
        try {
            notificationService.notifyAdminsSignupOtp(email);
        } catch (Exception notifyEx) {
            log.warn("Could not store signup OTP notification: {}", notifyEx.getMessage());
        }
    }

    private void notifyLoginOtp(String username) {
        try {
            notificationService.notifyAdminsLoginOtp(username);
        } catch (Exception e) {
            log.warn("OTP admin notification failed for {}: {}", username, e.getMessage());
        }
    }

    private boolean matchesCode(String stored, String provided) {
        if (stored == null || provided == null) {
            return false;
        }
        // Support legacy plaintext OTPs during migration window
        if (stored.length() <= 10 && stored.chars().allMatch(Character::isDigit)) {
            return stored.equals(provided);
        }
        try {
            return passwordEncoder.matches(provided, stored);
        } catch (Exception e) {
            return stored.equals(provided);
        }
    }

    private void invalidateOpenCodesForUser(User user) {
        if (user == null || user.getId() == null) {
            return;
        }
        mfaCodeRepository.findOpenForUser(user.getId(), LocalDateTime.now())
                .forEach(m -> {
                    m.setUsed(true);
                    mfaCodeRepository.save(m);
                });
    }

    private void invalidateOpenCodesForEmail(String email) {
        mfaCodeRepository.findOpenForEmail(email, LocalDateTime.now())
                .forEach(m -> {
                    m.setUsed(true);
                    mfaCodeRepository.save(m);
                });
    }

    private String generateSixDigitCode() {
        return String.valueOf(100000 + secureRandom.nextInt(900000));
    }

    private String getTermsAndConditions() {
        return "Terms and Conditions for System Use\n\n"
                + "Bayport Veterinary Clinic – Veterinary Clinic Management System (VCMS)\n\n"
                + "By creating an account you agree to protect clinic data and keep credentials confidential.\n";
    }
}
