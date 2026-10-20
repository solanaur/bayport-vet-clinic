package com.bayport.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenScopesTest {

    @Test
    void scopedTokensOnlyFitTheirEndpoints() {
        assertTrue(TokenScopes.allowed(TokenScopes.ACCESS, "GET", "/api/pets"));
        assertTrue(TokenScopes.allowed(TokenScopes.MFA_SETUP, "GET", "/api/me/totp-qr"));
        assertTrue(TokenScopes.allowed(TokenScopes.MFA_SETUP, "POST", "/api/me/totp/confirm"));
        assertFalse(TokenScopes.allowed(TokenScopes.MFA_SETUP, "GET", "/api/pets"));
        assertFalse(TokenScopes.allowed(TokenScopes.MFA_SETUP, "POST", "/api/me/totp/reenroll"));
        assertTrue(TokenScopes.allowed(TokenScopes.MFA, "POST", "/api/auth/mfa/verify"));
        assertFalse(TokenScopes.allowed(TokenScopes.MFA, "GET", "/api/me/totp-qr"));
        assertFalse(TokenScopes.allowed(null, "GET", "/api/health"));
    }
}
