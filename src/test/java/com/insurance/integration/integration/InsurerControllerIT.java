package com.insurance.integration.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.integration.common.enums.InsurerType;
import com.insurance.integration.insurer.dto.CreateInsurerRequest;
import com.insurance.integration.insurer.entity.Insurer;
import com.insurance.integration.insurer.repository.InsurerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for InsurerController.
 * Tests cover request validation, error handling, and API contract compliance.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("InsurerController Integration Tests")
class InsurerControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InsurerRepository insurerRepository;

    private CreateInsurerRequest validRequest;

    @BeforeEach
    @Transactional
    void setUp() {
        insurerRepository.deleteAll();

        validRequest = CreateInsurerRequest.builder()
                .insurerCode("TEST_LIFE")
                .insurerName("Test Life Insurance")
                .insurerType(InsurerType.LIFE)
                .contactEmail("test@life.com")
                .destinationBucketName("test-bucket")
                .destinationBucketRegion("us-east-1")
                .destinationIamRoleArn("arn:aws:iam::123456789012:role/test")
                .destinationPathPrefix("inbound/")
                .build();
    }

    // ─── POST /api/v1/admin/insurers ────────────────────────────────────

    @Test
    @DisplayName("POST /insurers: should register a new insurer and return 201")
    @Transactional
    void registerInsurer_success() throws Exception {
        mockMvc.perform(post("/api/v1/admin/insurers")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Insurer registered successfully"))
                .andExpect(jsonPath("$.data.insurerCode").value("TEST_LIFE"))
                .andExpect(jsonPath("$.data.insurerName").value("Test Life Insurance"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @DisplayName("POST /insurers: should reject duplicate insurer code with 409")
    @Transactional
    void registerInsurer_duplicateCode_conflict() throws Exception {
        // Register first insurer
        mockMvc.perform(post("/api/v1/admin/insurers")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        // Try to register duplicate
        mockMvc.perform(post("/api/v1/admin/insurers")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("POST /insurers: should validate required fields")
    @Transactional
    void registerInsurer_missingFields_badRequest() throws Exception {
        CreateInsurerRequest invalid = CreateInsurerRequest.builder()
                .insurerCode("TEST")
                // missing other required fields
                .build();

        mockMvc.perform(post("/api/v1/admin/insurers")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /insurers: should validate email format")
    @Transactional
    void registerInsurer_invalidEmail_badRequest() throws Exception {
        validRequest.setContactEmail("invalid-email");

        mockMvc.perform(post("/api/v1/admin/insurers")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /insurers: should validate IAM ARN format")
    @Transactional
    void registerInsurer_invalidIamArn_badRequest() throws Exception {
        validRequest.setDestinationIamRoleArn("invalid-arn");

        mockMvc.perform(post("/api/v1/admin/insurers")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── GET /api/v1/admin/insurers ────────────────────────────────────

    @Test
    @DisplayName("GET /insurers: should list insurers with pagination")
    @Transactional
    void listInsurers_success() throws Exception {
        mockMvc.perform(post("/api/v1/admin/insurers")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/insurers")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()", greaterThan(0)))
                .andExpect(jsonPath("$.pagination.totalElements", greaterThan(0)));
    }

    @Test
    @DisplayName("GET /insurers: should filter by active status")
    @Transactional
    void listInsurers_filterByActive() throws Exception {
        mockMvc.perform(post("/api/v1/admin/insurers")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/insurers")
                .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].active").value(true));
    }

    @Test
    @DisplayName("GET /insurers: should filter by insurer type")
    @Transactional
    void listInsurers_filterByType() throws Exception {
        mockMvc.perform(post("/api/v1/admin/insurers")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/insurers")
                .param("insurerType", "LIFE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].insurerType").value("LIFE"));
    }

    @Test
    @DisplayName("GET /insurers: should enforce max page size of 100")
    void listInsurers_maxPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/admin/insurers")
                .param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.pageSize").value(100));
    }

    // ─── GET /api/v1/admin/insurers/{insurerId} ────────────────────────

    @Test
    @DisplayName("GET /insurers/{id}: should return insurer by ID")
    @Transactional
    void getInsurer_success() throws Exception {
        mockMvc.perform(post("/api/v1/admin/insurers")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        Insurer insurer = insurerRepository.findByInsurerCode("TEST_LIFE").orElseThrow();

        mockMvc.perform(get("/api/v1/admin/insurers/" + insurer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.insurerCode").value("TEST_LIFE"));
    }

    @Test
    @DisplayName("GET /insurers/{id}: should return 404 for non-existent insurer")
    void getInsurer_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/admin/insurers/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /insurers/code/{code}: should return insurer by code")
    @Transactional
    void getInsurerByCode_success() throws Exception {
        mockMvc.perform(post("/api/v1/admin/insurers")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/insurers/code/TEST_LIFE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.insurerCode").value("TEST_LIFE"));
    }

    // ─── PUT /api/v1/admin/insurers/{insurerId}/activate ─────────────────

    @Test
    @DisplayName("PUT /insurers/{id}/activate: should activate deactivated insurer")
    @Transactional
    void activateInsurer_success() throws Exception {
        Insurer insurer = Insurer.builder()
                .insurerCode("INACTIVE_INSURER")
                .insurerName("Inactive Insurance")
                .insurerType(InsurerType.HEALTH)
                .contactEmail("health@test.com")
                .destinationBucketName("test-bucket")
                .destinationBucketRegion("us-east-1")
                .destinationIamRoleArn("arn:aws:iam::123456789012:role/test")
                .destinationPathPrefix("inbound/")
                .active(false)
                .build();
        insurer = insurerRepository.save(insurer);

        mockMvc.perform(put("/api/v1/admin/insurers/" + insurer.getId() + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    // ─── PUT /api/v1/admin/insurers/{insurerId}/deactivate ───────────────

    @Test
    @DisplayName("PUT /insurers/{id}/deactivate: should deactivate active insurer")
    @Transactional
    void deactivateInsurer_success() throws Exception {
        Insurer insurer = Insurer.builder()
                .insurerCode("ACTIVE_INSURER")
                .insurerName("Active Insurance")
                .insurerType(InsurerType.GENERAL)
                .contactEmail("general@test.com")
                .destinationBucketName("test-bucket")
                .destinationBucketRegion("us-east-1")
                .destinationIamRoleArn("arn:aws:iam::123456789012:role/test")
                .destinationPathPrefix("inbound/")
                .active(true)
                .build();
        insurer = insurerRepository.save(insurer);

        mockMvc.perform(put("/api/v1/admin/insurers/" + insurer.getId() + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    // ─── Security: Sensitive fields should NOT be exposed ────────────────

    @Test
    @DisplayName("GET /insurers: should never expose IAM ARN in response")
    @Transactional
    void getInsurer_sensitiveFieldsNotExposed() throws Exception {
        mockMvc.perform(post("/api/v1/admin/insurers")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        Insurer insurer = insurerRepository.findByInsurerCode("TEST_LIFE").orElseThrow();

        mockMvc.perform(get("/api/v1/admin/insurers/" + insurer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.destinationIamRoleArn").doesNotExist());
    }
}
