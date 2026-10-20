package com.bayport.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Existing desktop H2 files were created with an empty SA password.
 * When a password is configured, set it once before the connection pool starts.
 */
public class H2PasswordBootstrap implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Environment env = applicationContext.getEnvironment();
        String url = env.getProperty("spring.datasource.url", "");
        String user = env.getProperty("spring.datasource.username", "sa");
        String password = env.getProperty("spring.datasource.password", "");
        if (!url.startsWith("jdbc:h2:")) {
            return;
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Database password is missing. Set BAYPORT_DB_PASSWORD to at least 12 characters before starting.");
        }
        String probeUrl = url.contains("IFEXISTS=") ? url : url + ";IFEXISTS=TRUE";
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            return;
        }
        if (canConnect(probeUrl, user, password)) {
            System.out.println("H2 database already accepts the configured password.");
            return;
        }
        if (!canConnect(probeUrl, user, "")) {
            System.err.println("H2 password was not applied. The database did not accept the empty password either.");
            return;
        }
        String escaped = password.replace("'", "''");
        try (Connection connection = DriverManager.getConnection(url, user, "");
             Statement statement = connection.createStatement()) {
            statement.execute("ALTER USER " + user.toUpperCase() + " SET PASSWORD '" + escaped + "'");
            System.out.println("H2 database password updated for user " + user);
        } catch (SQLException e) {
            System.err.println("Could not set H2 password. The existing database did not accept the configured password.");
        }
    }

    private static boolean canConnect(String url, String user, String password) {
        try (Connection ignored = DriverManager.getConnection(url, user, password)) {
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
}
