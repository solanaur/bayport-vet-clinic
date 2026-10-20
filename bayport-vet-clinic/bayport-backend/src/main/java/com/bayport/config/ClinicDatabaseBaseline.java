package com.bayport.config;

import org.flywaydb.core.Flyway;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Creates the clinic schema on an empty MySQL database, then marks Flyway at version 13.
 * Later migrations still run. Existing databases that already have {@code users} are left untouched.
 */
public final class ClinicDatabaseBaseline {

    /**
     * mysql-clinic-schema.sql matches the schema after V13. Do not raise this unless that file is updated.
     */
    public static final String BASELINE_VERSION = "13";

    private ClinicDatabaseBaseline() {}

    public static boolean needsBaseline(boolean usersTableExists, boolean flywayHistoryExists) {
        return !usersTableExists && !flywayHistoryExists;
    }

    public static void prepareEmptyDatabase(DataSource dataSource) throws Exception {
        boolean users = tableExists(dataSource, "users");
        boolean history = tableExists(dataSource, "flyway_schema_history");
        if (!needsBaseline(users, history)) {
            return;
        }
        ClassPathResource script = new ClassPathResource("db/baseline/mysql-clinic-schema.sql");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, script);
        }
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineVersion(BASELINE_VERSION)
                .baselineDescription("Empty clinic database schema through V13")
                .load()
                .baseline();
    }

    private static boolean tableExists(DataSource dataSource, String table) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables "
                             + "WHERE table_schema = DATABASE() AND table_name = '" + table + "'")) {
            return result.next() && result.getInt(1) > 0;
        }
    }
}
