package com.insurance.integration.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import com.insurance.integration.common.enums.InsurerType;
import com.insurance.integration.common.enums.PartnerType;
import com.insurance.integration.insurer.entity.Insurer;
import com.insurance.integration.insurer.repository.InsurerRepository;
import com.insurance.integration.partner.entity.Partner;
import com.insurance.integration.partner.entity.PartnerInsurerMapping;
import com.insurance.integration.partner.repository.PartnerInsurerMappingRepository;
import com.insurance.integration.partner.repository.PartnerRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Module 0 integration test — verifies that:
 * 1. The Spring application context loads correctly.
 * 2. The database connection works.
 * 3. All Flyway migrations run successfully (V1 through V10).
 *
 * Uses Testcontainers to spin up a real PostgreSQL container.
 * Docker must be available for this test to run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Module 0 — Application Context Integration Tests (PostgreSQL/Testcontainers)")
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
        // The shared "test" profile defaults to H2; this class intentionally
        // exercises the real PostgreSQL + Flyway stack.
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private InsurerRepository insurerRepository;

    @Autowired
    private PartnerInsurerMappingRepository mappingRepository;

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
    @DisplayName("Flyway migrations run successfully (V1 through V10)")
    void flywayMigrationsRun() throws SQLException {
        var appliedMigrations = flyway.info().applied();
        assertThat(appliedMigrations)
                .as("Expected Flyway migrations V1–V10 to be applied")
                .extracting(migration -> migration.getVersion().getVersion())
                .contains("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");

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

    @Test
    @DisplayName("health endpoints report the application as up")
    void healthEndpointsWork() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Swagger UI and OpenAPI document are served")
    void swaggerAndOpenApiLoad() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.openapi").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/health']").exists());

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("global exception handler returns the standard not-found error shape")
    void globalNotFoundResponseIsStructured() throws Exception {
        mockMvc.perform(get("/api/v1/admin/partners/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errorType").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value(org.hamcrest.Matchers.startsWith(
                        "/api/v1/admin/partners/")));
    }

    @Test
    @Transactional
    @DisplayName("PostgreSQL persists and reloads partner-insurer relationships")
    void repositoriesPersistEntityRelationships() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        Partner partner = partnerRepository.saveAndFlush(Partner.builder()
                .partnerCode("PG_" + suffix)
                .partnerName("PostgreSQL Test Partner")
                .partnerType(PartnerType.BANK)
                .contactEmail("pg-" + suffix.toLowerCase() + "@example.com")
                .createdBy("test")
                .build());
        Insurer insurer = insurerRepository.saveAndFlush(Insurer.builder()
                .insurerCode("PG_" + suffix)
                .insurerName("PostgreSQL Test Insurer")
                .insurerType(InsurerType.GENERAL)
                .contactEmail("pg-insurer-" + suffix.toLowerCase() + "@example.com")
                .build());
        PartnerInsurerMapping mapping = mappingRepository.saveAndFlush(PartnerInsurerMapping.builder()
                .partner(partner)
                .insurer(insurer)
                .active(true)
                .createdBy("test")
                .build());

        PartnerInsurerMapping persisted = mappingRepository.findById(mapping.getId()).orElseThrow();

        assertThat(persisted.getPartner().getPartnerCode()).isEqualTo(partner.getPartnerCode());
        assertThat(persisted.getInsurer().getInsurerCode()).isEqualTo(insurer.getInsurerCode());
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
    }

    /**
     * Helper to check if a table exists in the public schema.
     */
    private boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet resultSet = connection.getMetaData().getTables(
                     connection.getCatalog(), "public", tableName, new String[]{"TABLE"})) {
            return resultSet.next();
        }
    }
}
