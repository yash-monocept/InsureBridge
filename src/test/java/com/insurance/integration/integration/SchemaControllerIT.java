package com.insurance.integration.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.integration.common.enums.FieldDataType;
import com.insurance.integration.schema.dto.FieldRuleCreateRequest;
import com.insurance.integration.schema.repository.ProductSchemaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Module 4 schema administration endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@DisplayName("Module 4 — Schema Controller Integration Tests")
class SchemaControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductSchemaRepository schemaRepository;

    @BeforeEach
    void cleanDatabase() {
        schemaRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /admin/schemas: creates a schema and returns it")
    void createSchema_success() throws Exception {
        String body = """
                {
                  "productType": "MOTOR_INSURANCE",
                  "schemaVersion": "1.0",
                  "schemaName": "Motor Insurance Schema v1.0",
                  "active": true
                }""";

        MvcResult result = mockMvc.perform(post("/api/v1/admin/schemas")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productType").value("MOTOR_INSURANCE"))
                .andExpect(jsonPath("$.data.schemaVersion").value("1.0"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();
        assertThat(UUID.fromString(id)).isNotNull();
    }

    @Test
    @DisplayName("POST /admin/schemas: missing product type returns 400")
    void createSchema_missingProductType_badRequest() throws Exception {
        String body = """
                {
                  "schemaVersion": "1.0",
                  "schemaName": "Schema",
                  "active": true
                }""";

        mockMvc.perform(post("/api/v1/admin/schemas")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /admin/schemas: lists schemas with pagination")
    void listSchemas_success() throws Exception {
        schemaRepository.save(com.insurance.integration.schema.entity.ProductSchema.builder()
                .productType("MOTOR_INSURANCE")
                .schemaVersion("1.0").schemaName("Motor v1.0").active(true).build());
        schemaRepository.save(com.insurance.integration.schema.entity.ProductSchema.builder()
                .productType("TRAVEL_INSURANCE")
                .schemaVersion("1.0").schemaName("Travel v1.0").active(true).build());

        MvcResult result = mockMvc.perform(get("/api/v1/admin/schemas")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records").isArray())
                .andExpect(jsonPath("$.totalRecords").value(2))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("records").path(0).path("schemaName").asText()).isNotBlank();
    }

    @Test
    @DisplayName("GET /admin/schemas/{id}: returns a schema by id")
    void getSchema_success() throws Exception {
        com.insurance.integration.schema.entity.ProductSchema saved = schemaRepository.save(
                com.insurance.integration.schema.entity.ProductSchema.builder()
                        .productType("MOTOR_INSURANCE")
                        .schemaVersion("1.0").schemaName("Motor v1.0").active(true).build());

        mockMvc.perform(get("/api/v1/admin/schemas/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schemaVersion").value("1.0"));
    }

    @Test
    @DisplayName("PUT /admin/schemas/{id}: updates schema name and active flag")
    void updateSchema_success() throws Exception {
        com.insurance.integration.schema.entity.ProductSchema saved = schemaRepository.save(
                com.insurance.integration.schema.entity.ProductSchema.builder()
                        .productType("MOTOR_INSURANCE")
                        .schemaVersion("1.0").schemaName("Motor v1.0").active(true).build());

        String body = """
                {
                  "schemaName": "Motor Insurance Schema v1.0 Renamed",
                  "active": false
                }""";

        mockMvc.perform(put("/api/v1/admin/schemas/{id}", saved.getId())
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schemaName").value("Motor Insurance Schema v1.0 Renamed"))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @DisplayName("POST /admin/schemas/{id}/rules: creates a field rule")
    void createFieldRule_success() throws Exception {
        com.insurance.integration.schema.entity.ProductSchema saved = schemaRepository.save(
                com.insurance.integration.schema.entity.ProductSchema.builder()
                        .productType("MOTOR_INSURANCE")
                        .schemaVersion("1.0").schemaName("Motor v1.0").active(true).build());

        String body = """
                {
                  "fieldPath": "customer.mobile",
                  "displayName": "Customer Mobile",
                  "required": true,
                  "dataType": "STRING",
                  "maxLength": 15
                }""";

        mockMvc.perform(post("/api/v1/admin/schemas/{id}/rules", saved.getId())
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fieldPath").value("customer.mobile"))
                .andExpect(jsonPath("$.data.dataType").value("STRING"));
    }

    @Test
    @DisplayName("GET /admin/schemas/{id}/rules: lists field rules")
    void listFieldRules_success() throws Exception {
        com.insurance.integration.schema.entity.ProductSchema saved = schemaRepository.save(
                com.insurance.integration.schema.entity.ProductSchema.builder()
                        .productType("MOTOR_INSURANCE")
                        .schemaVersion("1.0").schemaName("Motor v1.0").active(true).build());

        mockMvc.perform(get("/api/v1/admin/schemas/{id}/rules", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /admin/schemas/{id}/document-rules: lists document rules")
    void listDocumentRules_success() throws Exception {
        com.insurance.integration.schema.entity.ProductSchema saved = schemaRepository.save(
                com.insurance.integration.schema.entity.ProductSchema.builder()
                        .productType("MOTOR_INSURANCE")
                        .schemaVersion("1.0").schemaName("Motor v1.0").active(true).build());

        mockMvc.perform(get("/api/v1/admin/schemas/{id}/document-rules", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
