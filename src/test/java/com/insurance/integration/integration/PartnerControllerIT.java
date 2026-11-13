package com.insurance.integration.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.integration.common.enums.PartnerType;
import com.insurance.integration.partner.dto.CreateMappingRequest;
import com.insurance.integration.partner.dto.CreatePartnerRequest;
import com.insurance.integration.partner.dto.UpdatePartnerRequest;
import com.insurance.integration.partner.entity.Partner;
import com.insurance.integration.partner.repository.PartnerRepository;
import com.insurance.integration.insurer.entity.Insurer;
import com.insurance.integration.insurer.repository.InsurerRepository;
import com.insurance.integration.common.enums.InsurerType;
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
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for PartnerController.
 * Tests cover request validation, error handling, and API contract compliance.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("PartnerController Integration Tests")
class PartnerControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private InsurerRepository insurerRepository;

    private CreatePartnerRequest validRequest;
    private Insurer testInsurer;

    @BeforeEach
    @Transactional
    void setUp() {
        partnerRepository.deleteAll();
        insurerRepository.deleteAll();

        validRequest = CreatePartnerRequest.builder()
                .partnerCode("TEST_BANK")
                .partnerName("Test Bank Limited")
                .partnerType(PartnerType.BANK)
                .contactEmail("test@bank.com")
                .maxDocumentSizeMb(50)
                .build();

        testInsurer = Insurer.builder()
                .insurerCode("TEST_INSURER")
                .insurerName("Test Insurance")
                .insurerType(InsurerType.LIFE)
                .contactEmail("test@insurance.com")
                .destinationBucketName("test-bucket")
                .destinationBucketRegion("us-east-1")
                .destinationIamRoleArn("arn:aws:iam::123456789012:role/test")
                .destinationPathPrefix("test/")
                .active(true)
                .build();

        insurerRepository.save(testInsurer);
    }

    // ─── POST /api/v1/admin/partners ────────────────────────────────────

    @Test
    @DisplayName("POST /partners: should register a new partner and return 201")
    @Transactional
    void registerPartner_success() throws Exception {
        mockMvc.perform(post("/api/v1/admin/partners")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Partner registered successfully"))
                .andExpect(jsonPath("$.data.partnerCode").value("TEST_BANK"))
                .andExpect(jsonPath("$.data.partnerName").value("Test Bank Limited"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @DisplayName("POST /partners: should reject duplicate partner code with 409")
    @Transactional
    void registerPartner_duplicateCode_conflict() throws Exception {
        // Register first partner
        mockMvc.perform(post("/api/v1/admin/partners")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        // Try to register duplicate
        mockMvc.perform(post("/api/v1/admin/partners")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorType").isNotEmpty())
                .andExpect(jsonPath("$.errorType").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("POST /partners: should validate required fields")
    @Transactional
    void registerPartner_missingFields_badRequest() throws Exception {
        CreatePartnerRequest invalid = CreatePartnerRequest.builder()
                .partnerCode("TEST")
                // missing partnerName, partnerType, contactEmail
                .build();

        mockMvc.perform(post("/api/v1/admin/partners")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorType").isNotEmpty());
    }

    @Test
    @DisplayName("POST /partners: should validate email format")
    @Transactional
    void registerPartner_invalidEmail_badRequest() throws Exception {
        validRequest.setContactEmail("invalid-email");

        mockMvc.perform(post("/api/v1/admin/partners")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorType").isNotEmpty());
    }

    // ─── GET /api/v1/admin/partners ────────────────────────────────────

    @Test
    @DisplayName("GET /partners: should list partners with pagination")
    @Transactional
    void listPartners_success() throws Exception {
        // Create test partner
        mockMvc.perform(post("/api/v1/admin/partners")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        // List partners
        mockMvc.perform(get("/api/v1/admin/partners")
                .contentType(APPLICATION_JSON)
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.records.length()", greaterThan(0)))
                .andExpect(jsonPath("$.totalRecords", greaterThan(0)));
    }

    @Test
    @DisplayName("GET /partners: should filter by active status")
    @Transactional
    void listPartners_filterByActive() throws Exception {
        mockMvc.perform(post("/api/v1/admin/partners")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/partners")
                .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].active").value(true));
    }

    @Test
    @DisplayName("GET /partners: should enforce max page size of 100")
    void listPartners_maxPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/admin/partners")
                .param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(100));
    }

    // ─── GET /api/v1/admin/partners/{partnerId} ────────────────────────

    @Test
    @DisplayName("GET /partners/{id}: should return partner by ID")
    @Transactional
    void getPartner_success() throws Exception {
        // Create partner
        var response = mockMvc.perform(post("/api/v1/admin/partners")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = response.getResponse().getContentAsString();
        UUID partnerId = objectMapper.readTree(responseBody)
                .get("data").get("id").asText().equals("null") ?
                UUID.randomUUID() : UUID.fromString(objectMapper.readTree(responseBody)
                .get("data").get("id").asText());

        // Get partner - extract ID from response
        Partner partner = partnerRepository.findByPartnerCode("TEST_BANK").orElseThrow();

        mockMvc.perform(get("/api/v1/admin/partners/" + partner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.partnerCode").value("TEST_BANK"));
    }

    @Test
    @DisplayName("GET /partners/{id}: should return 404 for non-existent partner")
    void getPartner_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/admin/partners/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorType").isNotEmpty())
                .andExpect(jsonPath("$.errorType").value("NOT_FOUND"));
    }

    // ─── PUT /api/v1/admin/partners/{partnerId}/activate ─────────────────

    @Test
    @DisplayName("PUT /partners/{id}/activate: should activate deactivated partner")
    @Transactional
    void activatePartner_success() throws Exception {
        Partner partner = Partner.builder()
                .partnerCode("PARTNER1")
                .partnerName("Partner 1")
                .partnerType(PartnerType.BANK)
                .contactEmail("p1@test.com")
                .active(false)
                .createdBy("admin")
                .build();
        partner = partnerRepository.save(partner);

        mockMvc.perform(put("/api/v1/admin/partners/" + partner.getId() + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    // ─── PUT /api/v1/admin/partners/{partnerId}/deactivate ───────────────

    @Test
    @DisplayName("PUT /partners/{id}/deactivate: should deactivate active partner")
    @Transactional
    void deactivatePartner_success() throws Exception {
        Partner partner = Partner.builder()
                .partnerCode("PARTNER2")
                .partnerName("Partner 2")
                .partnerType(PartnerType.AIRLINE)
                .contactEmail("p2@test.com")
                .active(true)
                .createdBy("admin")
                .build();
        partner = partnerRepository.save(partner);

        mockMvc.perform(put("/api/v1/admin/partners/" + partner.getId() + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    // ─── POST /api/v1/admin/partners/{partnerId}/mappings ────────────────

    @Test
    @DisplayName("POST /partners/{id}/mappings: should create partner-insurer mapping")
    @Transactional
    void createMapping_success() throws Exception {
        Partner partner = Partner.builder()
                .partnerCode("PARTNER3")
                .partnerName("Partner 3")
                .partnerType(PartnerType.BANK)
                .contactEmail("p3@test.com")
                .active(true)
                .createdBy("admin")
                .build();
        partner = partnerRepository.save(partner);

        CreateMappingRequest request = new CreateMappingRequest();
        request.setInsurerCode("TEST_INSURER");
        request.setPermittedProductTypes("[\"MOTOR_INSURANCE\", \"HEALTH_INSURANCE\"]");

        mockMvc.perform(post("/api/v1/admin/partners/" + partner.getId() + "/mappings")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.insurerCode").value("TEST_INSURER"));
    }

    @Test
    @DisplayName("POST /partners/{id}/mappings: should reject invalid insurer")
    @Transactional
    void createMapping_invalidInsurer_notFound() throws Exception {
        Partner partner = Partner.builder()
                .partnerCode("PARTNER4")
                .partnerName("Partner 4")
                .partnerType(PartnerType.BANK)
                .contactEmail("p4@test.com")
                .active(true)
                .createdBy("admin")
                .build();
        partner = partnerRepository.save(partner);

        CreateMappingRequest request = new CreateMappingRequest();
        request.setInsurerCode("NONEXISTENT_INSURER");
        request.setPermittedProductTypes("[\"MOTOR_INSURANCE\"]");

        mockMvc.perform(post("/api/v1/admin/partners/" + partner.getId() + "/mappings")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorType").isNotEmpty());
    }

    // ─── GET /api/v1/admin/partners/{partnerId}/mappings ────────────────

    @Test
    @DisplayName("GET /partners/{id}/mappings: should list partner's active mappings")
    @Transactional
    void getMappings_success() throws Exception {
        Partner partner = Partner.builder()
                .partnerCode("PARTNER5")
                .partnerName("Partner 5")
                .partnerType(PartnerType.BANK)
                .contactEmail("p5@test.com")
                .active(true)
                .createdBy("admin")
                .build();
        partner = partnerRepository.save(partner);

        mockMvc.perform(get("/api/v1/admin/partners/" + partner.getId() + "/mappings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}
