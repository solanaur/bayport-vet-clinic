package com.bayport.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Existing H2 files can predate newer NOT NULL columns. Hibernate's ddl-auto=update
 * cannot add those columns when rows already exist, which then breaks notifications
 * and appointment create. Patch them with defaults at startup.
 */
@Component
@Order(1)
public class DesktopSchemaPatcher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DesktopSchemaPatcher.class);

    private final DataSource dataSource;

    public DesktopSchemaPatcher(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            execQuiet(statement, "ALTER TABLE notifications ADD COLUMN IF NOT EXISTS is_read BOOLEAN DEFAULT FALSE");
            execQuiet(statement, "UPDATE notifications SET is_read = FALSE WHERE is_read IS NULL");
            patchLegacyReadColumn(connection, statement);
            execQuiet(statement, "ALTER TABLE mfa_code ADD COLUMN IF NOT EXISTS attempt_count INT DEFAULT 0");
            execQuiet(statement, "UPDATE mfa_code SET attempt_count = 0 WHERE attempt_count IS NULL");
            execQuiet(statement, "ALTER TABLE print_jobs ADD COLUMN IF NOT EXISTS attempt_count INT DEFAULT 0");
            execQuiet(statement, "UPDATE print_jobs SET attempt_count = 0 WHERE attempt_count IS NULL");
            execQuiet(statement, "ALTER TABLE users ADD COLUMN IF NOT EXISTS totp_secret VARCHAR(64)");
            log.info("Desktop schema columns verified (is_read, READ, attempt_count, totp_secret)");
        } catch (Exception e) {
            log.warn("Desktop schema patch skipped: {}", e.getMessage());
        }
    }

    /**
     * Older H2 schemas kept a reserved {@code READ} column that is NOT NULL with no default.
     * Hibernate only writes {@code is_read}, so OTP admin-notification inserts fail.
     */
    private void patchLegacyReadColumn(Connection connection, Statement statement) {
        if (!hasColumn(connection, "NOTIFICATIONS", "READ")) {
            return;
        }
        execQuiet(statement, "UPDATE notifications SET \"READ\" = FALSE WHERE \"READ\" IS NULL");
        execQuiet(statement, "ALTER TABLE notifications ALTER COLUMN \"READ\" SET DEFAULT FALSE");
        execQuiet(statement, "ALTER TABLE notifications ALTER COLUMN \"READ\" SET NULL");
    }

    private static boolean hasColumn(Connection connection, String table, String column) {
        try {
            DatabaseMetaData meta = connection.getMetaData();
            if (columnExists(meta, table, column) || columnExists(meta, table.toLowerCase(), column.toLowerCase())) {
                return true;
            }
        } catch (Exception e) {
            log.debug("Could not inspect {}.{}: {}", table, column, e.getMessage());
        }
        return false;
    }

    private static boolean columnExists(DatabaseMetaData meta, String table, String column) throws Exception {
        try (ResultSet rs = meta.getColumns(null, null, table, column)) {
            return rs.next();
        }
    }

    private static void execQuiet(Statement statement, String sql) {
        try {
            statement.execute(sql);
        } catch (Exception e) {
            log.debug("Schema statement skipped: {} ({})", sql, e.getMessage());
        }
    }
}
