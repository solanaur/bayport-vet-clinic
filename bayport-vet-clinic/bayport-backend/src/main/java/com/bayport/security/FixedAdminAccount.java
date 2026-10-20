package com.bayport.security;

/**
 * The clinic administrator username is always {@code R_Admin}.
 * The password stays in the local environment and is never stored in source.
 */
public final class FixedAdminAccount {

    public static final String USERNAME = "R_Admin";

    public enum Plan {
        /** The account is already in this database. Leave the password and authenticator alone. */
        KEEP,
        /** Create the account from the local administrator password. */
        CREATE,
        /** No local password was provided, so nothing is created. */
        MISSING_PASSWORD
    }

    private FixedAdminAccount() {}

    public static Plan plan(boolean userExists, String password) {
        if (userExists) {
            return Plan.KEEP;
        }
        if (password == null || password.isBlank()) {
            return Plan.MISSING_PASSWORD;
        }
        return Plan.CREATE;
    }
}
