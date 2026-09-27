package com.bayport.auth;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * RFC 6238 TOTP (Google Authenticator): 6 digits, 30-second steps, HMAC-SHA1.
 */
@Service
public class TotpService {

    public static final String ISSUER = "Bayport Veterinary Clinic";
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20;
    private static final int DIGITS = 6;
    private static final int PERIOD_SECONDS = 30;
    private static final int WINDOW = 1;

    private final SecureRandom secureRandom = new SecureRandom();

    public record Enrollment(String secret, String otpauthUri) {}

    public Enrollment enroll(String username) {
        String secret = generateSecret();
        return new Enrollment(secret, otpauthUri(username, secret));
    }

    public String generateSecret() {
        byte[] raw = new byte[SECRET_BYTES];
        secureRandom.nextBytes(raw);
        return encodeBase32(raw);
    }

    public String otpauthUri(String username, String secret) {
        String account = username == null || username.isBlank() ? "user" : username.trim();
        String label = url(ISSUER) + ":" + url(account);
        return "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=" + url(ISSUER)
                + "&algorithm=SHA1"
                + "&digits=" + DIGITS
                + "&period=" + PERIOD_SECONDS;
    }

    public boolean isEnrolled(String secret) {
        return secret != null && !secret.isBlank();
    }

    public boolean verify(String secret, String code) {
        String normalized = code == null ? "" : code.trim().replace(" ", "");
        if (!isEnrolled(secret) || !normalized.matches("\\d{6}")) {
            return false;
        }
        byte[] key;
        try {
            key = decodeBase32(secret);
        } catch (IllegalArgumentException e) {
            return false;
        }
        long step = System.currentTimeMillis() / 1000L / PERIOD_SECONDS;
        for (int i = -WINDOW; i <= WINDOW; i++) {
            if (constantTimeEquals(normalized, generateCode(key, step + i))) {
                return true;
            }
        }
        return false;
    }

    private static String generateCode(byte[] key, long step) {
        try {
            byte[] data = ByteBuffer.allocate(8).putLong(step).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int otp = binary % 1_000_000;
            return String.format(Locale.ROOT, "%06d", otp);
        } catch (Exception e) {
            return "";
        }
    }

    private static String encodeBase32(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                out.append(BASE32.charAt((buffer >> (bitsLeft - 5)) & 31));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            out.append(BASE32.charAt((buffer << (5 - bitsLeft)) & 31));
        }
        return out.toString();
    }

    private static byte[] decodeBase32(String value) {
        String compact = value.trim().toUpperCase(Locale.ROOT).replace("=", "").replace(" ", "");
        int buffer = 0;
        int bitsLeft = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < compact.length(); i++) {
            int idx = BASE32.indexOf(compact.charAt(i));
            if (idx < 0) {
                throw new IllegalArgumentException("Invalid TOTP secret");
            }
            buffer = (buffer << 5) | idx;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
