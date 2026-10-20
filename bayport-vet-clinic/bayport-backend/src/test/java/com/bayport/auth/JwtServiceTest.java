package com.bayport.auth;

import com.bayport.security.TokenScopes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "12345678901234567890123456789012";

    @Test
    void missingOrShortSecretFailsInsteadOfPadding() {
        assertThrows(IllegalStateException.class, () -> new JwtService("", 60_000));
        assertThrows(IllegalStateException.class, () -> new JwtService("short-secret", 60_000));
    }

    @Test
    void issuesDistinctScopesAndBindsAccessTokenToVersion() {
        JwtService jwt = new JwtService(SECRET, 60_000);
        String access = jwt.generateAccessToken("ada", 3);
        String mfa = jwt.generateMfaToken("ada");
        String setup = jwt.generateMfaSetupToken("ada");

        assertEquals(TokenScopes.ACCESS, jwt.extractScope(access));
        assertEquals(3, jwt.extractTokenVersion(access));
        assertTrue(jwt.isAccessTokenValid(access, "ada", 3));
        assertFalse(jwt.isAccessTokenValid(access, "ada", 4));
        assertFalse(jwt.isAccessTokenValid(mfa, "ada", 3));

        assertEquals(TokenScopes.MFA, jwt.extractScope(mfa));
        assertTrue(jwt.isScopedTokenValid(mfa, TokenScopes.MFA));
        assertFalse(jwt.isScopedTokenValid(mfa, TokenScopes.MFA_SETUP));
        assertEquals("ada", jwt.extractUsername(setup));
        assertTrue(jwt.extractExpiration(mfa).getTime() - System.currentTimeMillis() <= JwtService.MFA_TOKEN_MS);
        assertTrue(jwt.extractExpiration(setup).getTime() - System.currentTimeMillis() <= JwtService.MFA_SETUP_TOKEN_MS);
    }
}
