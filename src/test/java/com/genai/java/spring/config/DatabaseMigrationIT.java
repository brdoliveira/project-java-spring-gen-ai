package com.genai.java.spring.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class DatabaseMigrationIT {

    @Test
    @DisplayName("@spec:AC-003 Main database migrations preserve existing records")
    void mainDatabaseMigrationsPreserveExistingRecords() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4-alpine")) {
            postgres.start();
            Flyway flyway = flyway(postgres);
            assertEquals(2, flyway.migrate().migrationsExecuted);

            UUID id = UUID.randomUUID();
            try (Connection connection = connection(postgres);
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO review_state_snapshot (id, owner_subject, status) VALUES ('"
                        + id + "', 'migration-test', 'DONE')");
            }

            assertEquals(0, flyway(postgres).migrate().migrationsExecuted);
            try (Connection connection = connection(postgres);
                    Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery(
                            "SELECT count(*) FROM review_state_snapshot WHERE id = '" + id + "' AND status = 'DONE'")) {
                result.next();
                assertEquals(1, result.getInt(1));
            }
        }
    }

    private static Flyway flyway(PostgreSQLContainer<?> postgres) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .cleanDisabled(true)
                .load();
    }

    private static Connection connection(PostgreSQLContainer<?> postgres) throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
