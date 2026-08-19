package com.genai.posture.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class PostureMigrationIT {

    @Test
    @DisplayName("@spec:AC-003 Posture migrations preserve existing records")
    void postureMigrationsPreserveExistingRecords() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4-alpine")) {
            postgres.start();
            Flyway flyway = flyway(postgres);
            assertEquals(1, flyway.migrate().migrationsExecuted);

            try (Connection connection = connection(postgres);
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO security_posture (service_id, environment)
                        VALUES ('existing-service', 'prod')
                        """);
            }

            assertEquals(0, flyway(postgres).migrate().migrationsExecuted);
            try (Connection connection = connection(postgres);
                    Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("""
                            SELECT count(*) FROM security_posture
                            WHERE service_id = 'existing-service' AND environment = 'prod'
                            """)) {
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
