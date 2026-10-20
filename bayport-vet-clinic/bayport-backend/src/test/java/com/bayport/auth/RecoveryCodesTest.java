package com.bayport.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryCodesTest {

    @Test
    void generatesTenSingleUseCodes() {
        List<String> codes = RecoveryCodes.generate(new SecureRandom());
        assertEquals(RecoveryCodes.COUNT, codes.size());
        assertEquals(codes.size(), new HashSet<>(codes).size());
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode(RecoveryCodes.normalize(codes.get(0)));
        assertTrue(encoder.matches(RecoveryCodes.normalize(" " + codes.get(0).toLowerCase() + " "), hash));
        assertTrue(codes.get(0).matches("[A-Z2-9]{4}-[A-Z2-9]{4}"));
    }
}
