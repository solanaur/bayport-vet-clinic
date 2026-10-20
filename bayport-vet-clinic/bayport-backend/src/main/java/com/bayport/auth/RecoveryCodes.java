package com.bayport.auth;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One-time backup codes for when Google Authenticator is unavailable.
 * Shown once, stored only as a hash.
 */
public final class RecoveryCodes {

    public static final int COUNT = 10;
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private RecoveryCodes() {}

    public static List<String> generate(SecureRandom random) {
        Set<String> codes = new LinkedHashSet<>();
        while (codes.size() < COUNT) {
            codes.add(randomCode(random));
        }
        return new ArrayList<>(codes);
    }

    public static String normalize(String presented) {
        if (presented == null) {
            return "";
        }
        return presented.trim().replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private static String randomCode(SecureRandom random) {
        char[] raw = new char[8];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(raw, 0, 4) + "-" + new String(raw, 4, 4);
    }
}
