package com.insurance.integration.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Module 0 integration test — verifies that:
 * 1. The Spring application context loads correctly.
 * 2. The database connection works.
 * 3. All Flyway migrations run successfully (V1 through V9).
 *
 * Uses Testcontainers to spin up a real PostgreSQL container.
 * Docker must be available for this test to run.
 */
@SpringBootTest
@Testcontainers
@DisplayName("Module 0 — Application Context Integration Tests")
class ApplicationContextTest {

    /**
     * PostgreSQL container — started once per test class by Testcontainers.
     * Uses the same PostgreSQL major version as production.
     */
    @Container
    static PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("insurance_integration_test")
                    .withUsername("test_user")
                    .withPassword("test_password");

    /**
     * Wire the Testcontainers PostgreSQL URL into the Spring DataSource
     * before the application context is created.
     */
    @DynamicPropertySource
    static void configureDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        // Ensure Flyway clean is allowed in test so reruns work cleanly
        registry.add("spring.flyway.clean-disabled", () -> "false");
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    /**
     * Verifies that the Spring application context starts without errors.
     * This test implicitly validates that all beans, configurations, and
     * auto-wiring resolve correctly.
     */
    @Test
    @DisplayName("Application context loads without errors")
    void contextLoads() {
        // If the context fails to load, the test class itself fails to initialise.
        // Reaching this point means the context loaded successfully.
        assertThat(dataSource).isNotNull();
        assertThat(flyway).isNotNull();
    }

    /**
     * Verifies that a real database connection can be obtained and a simple
     * query executes successfully against the Testcontainers PostgreSQL instance.
     */
    @Test
    @DisplayName("Database connection is functional")
    void databaseConnectionWorks() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {

            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);
        }
    }

    /**
     * Verifies that all Flyway migrations ran successfully.
     * We expect at least V1 through V9 (9 migrations) to be applied.
     */
    @Test
    @DisplayName("Flyway migrations run successfully (V1 through V9)")
    void flywayMigrationsRun() {
        var appliedMigrations = flyway.info().applied();
        assertThat(appliedMigrations)
                .as("Expected at least 9 Flyway migrations (V1–V9) to be applied")
                .hasSizeGreaterThanOrEqualTo(9);

        // Verify the partners table exists (created by V1)
        boolean partnersTableExists = tableExists("partners");
        assertThat(partnersTableExists)
                .as("partners table should exist after V1 migration")
                .isTrue();

        // Verify the product_schemas table exists (created by V3)
        boolean productSchemasTableExists = tableExists("product_schemas");
        assertThat(productSchemasTableExists)
                .as("product_schemas table should exist after V3 migration")
                .isTrue();

        // Verify the outbox_events table exists (created by V7)
        boolean outboxEventsTableExists = tableExists("outbox_events");
        assertThat(outboxEventsTableExists)
                .as("outbox_events table should exist after V7 migration")
                .isTrue();
    }

    /**
     * Helper to check if a table exists in the public schema.
     */
    private boolean tableExists(String tableName) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables " +
                     "WHERE table_schema = 'public' AND table_name = '" + tableName + "'")) {

            if (resultSet.next()) {
                return resultSet.getInt(1) > 0;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
