package com.bayport.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

/**
 * Handles database migration for new security columns.
 * This runs before DataInitializer to ensure schema is ready.
 */
@Component
@Order(1) // Run before DataInitializer
public class DatabaseMigration implements CommandLineRunner {

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String product = safeProduct(meta);
            String dateTimeSql = dateTimeType(product);

            // Check and add password column to users
            if (!columnExists(meta, "USERS", "PASSWORD")) {
                conn.createStatement().execute("ALTER TABLE users ADD COLUMN password VARCHAR(255) NULL");
                conn.createStatement().execute("UPDATE users SET password = password_hash WHERE password IS NULL AND password_hash IS NOT NULL");
            }

            // Check and add mfa_enabled column to users
            if (!columnExists(meta, "USERS", "MFA_ENABLED")) {
                conn.createStatement().execute("ALTER TABLE users ADD COLUMN mfa_enabled BOOLEAN NULL");
                conn.createStatement().execute("UPDATE users SET mfa_enabled = FALSE WHERE mfa_enabled IS NULL");
            }

            // Check and add full_name column to users
            if (!columnExists(meta, "USERS", "FULL_NAME")) {
                conn.createStatement().execute("ALTER TABLE users ADD COLUMN full_name VARCHAR(255) NULL");
                conn.createStatement().execute("UPDATE users SET full_name = name WHERE full_name IS NULL AND name IS NOT NULL");
            }

            // Check and add email column to users
            if (!columnExists(meta, "USERS", "EMAIL")) {
                conn.createStatement().execute("ALTER TABLE users ADD COLUMN email VARCHAR(255) NULL");
            }

            // Check and add TOS columns to users
            if (!columnExists(meta, "USERS", "TOS_VERSION_ACCEPTED")) {
                conn.createStatement().execute("ALTER TABLE users ADD COLUMN tos_version_accepted VARCHAR(20) NULL");
            }
            if (!columnExists(meta, "USERS", "TOS_ACCEPTED_AT")) {
                conn.createStatement().execute("ALTER TABLE users ADD COLUMN tos_accepted_at " + dateTimeSql + " NULL");
            }

            // Check and add soft delete columns to pets
            if (!columnExists(meta, "PETS", "DELETED")) {
                conn.createStatement().execute("ALTER TABLE pets ADD COLUMN deleted BOOLEAN NULL");
                conn.createStatement().execute("UPDATE pets SET deleted = FALSE WHERE deleted IS NULL");
            }
            if (!columnExists(meta, "PETS", "DELETED_AT")) {
                conn.createStatement().execute("ALTER TABLE pets ADD COLUMN deleted_at " + dateTimeSql + " NULL");
            }
            if (!columnExists(meta, "PETS", "DELETED_BY")) {
                conn.createStatement().execute("ALTER TABLE pets ADD COLUMN deleted_by VARCHAR(100) NULL");
            }

            // Fix MFA_CODE table - make user_id nullable if not already
            try {
                if (product.contains("mysql")) {
                    conn.createStatement().execute("ALTER TABLE mfa_code MODIFY COLUMN user_id BIGINT NULL");
                } else if (product.contains("postgres")) {
                    conn.createStatement().execute("ALTER TABLE mfa_code ALTER COLUMN user_id DROP NOT NULL");
                } else {
                    conn.createStatement().execute("ALTER TABLE mfa_code ALTER COLUMN user_id SET NULL");
                }
            } catch (Exception e) {
                // Column might already be nullable or table doesn't exist yet - ignore
            }

            System.out.println("[DatabaseMigration] Schema migration completed successfully");
        } catch (Exception e) {
            System.err.println("[DatabaseMigration] Migration error (may be harmless): " + e.getMessage());
            // Don't fail startup - Hibernate will handle schema updates
        }
    }

    private static String safeProduct(DatabaseMetaData meta) {
        try {
            String p = meta.getDatabaseProductName();
            return p == null ? "" : p.toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }

    private static String dateTimeType(String productLower) {
        if (productLower.contains("postgres")) {
            return "TIMESTAMP";
        }
        return "DATETIME";
    }

    private boolean columnExists(DatabaseMetaData meta, String tableName, String columnName) throws Exception {
        // Try both cases - MySQL returns lowercase, H2 returns uppercase
        for (String t : new String[]{tableName, tableName.toLowerCase(), tableName.toUpperCase()}) {
            for (String c : new String[]{columnName, columnName.toLowerCase(), columnName.toUpperCase()}) {
                try (ResultSet rs = meta.getColumns(null, null, t, c)) {
                    if (rs.next()) return true;
                }
            }
        }
        return false;
    }
}
