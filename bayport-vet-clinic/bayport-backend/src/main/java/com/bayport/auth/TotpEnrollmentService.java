package com.bayport.auth;

import org.springframework.stereotype.Service;

/**
 * Encrypts TOTP secrets before they are stored and decrypts them only to check a code or build a QR URI.
 */
@Service
public class TotpEnrollmentService {

    public record EncryptedEnrollment(String encryptedSecret, String otpauthUri) {}

    private final TotpService totpService;
    private final TotpSecretCipher cipher;

    public TotpEnrollmentService(TotpService totpService, TotpSecretCipher cipher) {
        this.totpService = totpService;
        this.cipher = cipher;
    }

    public EncryptedEnrollment enroll(String username) {
        TotpService.Enrollment enrollment = totpService.enroll(username);
        return new EncryptedEnrollment(cipher.encrypt(enrollment.secret()), enrollment.otpauthUri());
    }

    public String otpauthUri(String username, String storedSecret) {
        return totpService.otpauthUri(username, cipher.decrypt(storedSecret));
    }

    public Long matchingStep(String storedSecret, String code) {
        if (storedSecret == null || storedSecret.isBlank()) {
            return null;
        }
        return totpService.matchingStep(cipher.decrypt(storedSecret), code);
    }

    public String encryptExisting(String stored) {
        return cipher.encrypt(stored);
    }

    public boolean isEncrypted(String stored) {
        return cipher.isEncrypted(stored);
    }
}
