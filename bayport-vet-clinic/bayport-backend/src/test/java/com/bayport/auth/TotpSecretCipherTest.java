package com.bayport.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpSecretCipherTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";

    @Test
    void encryptsAndStillReadsLegacyPlaintext() {
        assertThrows(IllegalStateException.class, () -> new TotpSecretCipher("too-short"));
        TotpSecretCipher cipher = new TotpSecretCipher(KEY);
        String stored = cipher.encrypt("JBSWY3DPEHPK3PXP");
        assertTrue(cipher.isEncrypted(stored));
        assertFalse(stored.contains("JBSWY3DPEHPK3PXP"));
        assertEquals("JBSWY3DPEHPK3PXP", cipher.decrypt(stored));
        assertEquals("PLAINTEXTSECRET", cipher.decrypt("PLAINTEXTSECRET"));
        assertEquals(stored, cipher.encrypt(stored));
    }
}
