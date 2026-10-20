package com.bayport.security;

public final class LoginGate {

    private LoginGate() {}

    public static boolean mustReplacePassword(String typedPassword, boolean flaggedOnAccount) {
        return flaggedOnAccount || PasswordPolicy.isFactoryDefault(typedPassword);
    }
}
