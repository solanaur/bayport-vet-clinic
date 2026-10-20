package com.bayport.security;

/**
 * JWT scope values. Scoped tokens are accepted only on the endpoints that issued them.
 */
public final class TokenScopes {

    public static final String ACCESS = "access";
    public static final String MFA = "mfa";
    public static final String MFA_SETUP = "mfa_setup";

    private TokenScopes() {}

    public static boolean allowed(String scope, String method, String path) {
        String verb = method == null ? "" : method.trim().toUpperCase();
        String uri = path == null ? "" : path;
        if (ACCESS.equals(scope)) {
            return true;
        }
        if (MFA_SETUP.equals(scope)) {
            return ("GET".equals(verb) && uri.endsWith("/me/totp-qr"))
                    || ("POST".equals(verb) && uri.endsWith("/me/totp/confirm"));
        }
        if (MFA.equals(scope)) {
            return "POST".equals(verb) && uri.endsWith("/auth/mfa/verify");
        }
        return false;
    }
}
