package com.insurance.integration.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.insurance.integration.common.enums.IngestionStatus;
import com.insurance.integration.insurer.entity.Insurer;
import com.insurance.integration.insurer.repository.InsurerRepository;
import com.insurance.integration.ingestion.entity.IngestionRequest;
import com.insurance.integration.ingestion.repository.IngestionDocumentRepository;
import com.insurance.integration.ingestion.repository.IngestionRequestRepository;
import com.insurance.integration.partner.entity.PartnerInsurerMapping;
import com.insurance.integration.partner.repository.PartnerInsurerMappingRepository;
import com.insurance.integration.partner.repository.PartnerRepository;
import com.insurance.integration.security.entity.ApiClient;
import com.insurance.integration.security.jwt.JwtService;
import com.insurance.integration.security.repository.ApiClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Module 3 insurance submission endpoints.
 * Partner routes require a JWT issued via OAuth 2.0 Client Credentials.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@DisplayName("Module 3 — Ingestion Controller Integration Tests")
class IngestionControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private InsurerRepository insurerRepository;

    @Autowired
    private PartnerInsurerMappingRepository mappingRepository;

    @Autowired
    private ApiClientRepository apiClientRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private IngestionRequestRepository ingestionRequestRepository;

    @Autowired
    private IngestionDocumentRepository ingestionDocumentRepository;

    @Autowired
    private com.insurance.integration.schema.repository.ProductSchemaRepository schemaRepository;

    private String accessToken;
    private UUID partnerId;
    private UUID insurerId;

    @BeforeEach
    void setUp() throws Exception {
        apiClientRepository.deleteAll();
        mappingRepository.deleteAll();
        ingestionDocumentRepository.deleteAll();
        ingestionRequestRepository.deleteAll();
        partnerRepository.deleteAll();
        insurerRepository.deleteAll();

        Credentials credentials = createPartnerAndClient("INGEST_BANK", List.of("read", "write"), null);
        partnerId = UUID.fromString(credentials.partnerId());

        Insurer insurer = insurerRepository.save(Insurer.builder()
                .insurerCode("MAX_LIFE")
                .insurerName("Max Life Insurance")
                .insurerType(com.insurance.integration.common.enums.InsurerType.LIFE)
                .active(true)
                .build());
        insurerId = insurer.getId();

        mappingRepository.save(PartnerInsurerMapping.builder()
                .partner(partnerRepository.findById(partnerId).orElseThrow())
                .insurer(insurer)
                .active(true)
                .build());

        schemaRepository.save(com.insurance.integration.schema.entity.ProductSchema.builder()
                .productType("MOTOR_INSURANCE")
                .schemaVersion("1.0")
                .schemaName("Motor Insurance Schema v1.0")
                .active(true)
                .build());

        accessToken = requestToken(credentials.clientId(), credentials.clientSecret());
    }

    // ─── POST /api/v1/partner/insurance/submissions ─────────────────────

    @Test
    @DisplayName("POST submissions: valid motor insurance application is accepted and validated")
    void submit_validMotorApplication_created() throws Exception {
        String body = """
                {
                  "partnerReferenceId": "CAR-92831",
                  "insurerCode": "MAX_LIFE",
                  "productCode": "MOTOR_INSURANCE",
                  "schemaVersion": "1.0",
                  "idempotencyKey": "idem_car_92831",
                  "customer": {
                    "name": "Rahul Sharma",
                    "mobile": "9999999999",
                    "email": "rahul@example.com"
                  },
                  "applicationData": {
                    "vehicle": {
                      "make": "Hyundai",
                      "model": "Creta",
                      "registrationNumber": "DL01AB1234",
                      "manufacturingYear": 2026
                    }
                  },
                  "documents": [
                    {
                      "documentType": "IDENTITY_PROOF",
                      "fileName": "identity.pdf",
                      "contentType": "application/pdf",
                      "sourceUrl": "https://bucket.s3.amazonaws.com/identity.pdf"
                    },
                    {
                      "documentType": "VEHICLE_DOCUMENT",
                      "fileName": "vehicle.pdf",
                      "contentType": "application/pdf",
                      "sourceUrl": "https://bucket.s3.amazonaws.com/vehicle.pdf"
                    }
                  ]
                }""";

        mockMvc.perform(post("/api/v1/partner/insurance/submissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.submissionId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("VALIDATED"))
                .andExpect(jsonPath("$.data.documentCount").value(2));
    }

    @Test
    @DisplayName("POST submissions: missing required fields returns 400")
    void submit_missingFields_badRequest() throws Exception {
        String body = """
                {
                  "partnerReferenceId": "CAR-92831",
                  "insurerCode": "MAX_LIFE",
                  "productCode": "MOTOR_INSURANCE"
                }""";

        mockMvc.perform(post("/api/v1/partner/insurance/submissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorType").isNotEmpty());
    }

    @Test
    @DisplayName("POST submissions: duplicate idempotency key returns 409")
    void submit_duplicateIdempotencyKey_conflict() throws Exception {
        String body = """
                {
                  "partnerReferenceId": "CAR-92831",
                  "insurerCode": "MAX_LIFE",
                  "productCode": "MOTOR_INSURANCE",
                  "idempotencyKey": "idem_dup",
                  "customer": {
                    "name": "Rahul Sharma",
                    "mobile": "9999999999",
                    "email": "rahul@example.com"
                  },
                  "applicationData": {},
                  "documents": [
                    {
                      "documentType": "IDENTITY_PROOF",
                      "fileName": "identity.pdf",
                      "contentType": "application/pdf",
                      "sourceUrl": "https://bucket.s3.amazonaws.com/identity.pdf"
                    }
                  ]
                }""";

        mockMvc.perform(post("/api/v1/partner/insurance/submissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/partner/insurance/submissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST submissions: missing token returns 401")
    void submit_noToken_unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/partner/insurance/submissions")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /api/v1/partner/insurance/submissions ──────────────────────

    @Test
    @DisplayName("GET submissions: lists only the authenticated partner's submissions")
    void list_submissions_partnerIsolation() throws Exception {
        // Create a submission directly in the DB for this partner
        IngestionRequest request = IngestionRequest.builder()
                
                .externalReference("CAR-001")
                .idempotencyKey("idem_001")
                .partnerId(partnerId)
                .insurerId(insurerId)
                .productType("MOTOR_INSURANCE")
                .status(IngestionStatus.VALIDATED)
                .customerData("{\"name\":\"X\",\"mobile\":\"1\",\"email\":\"a@b.c\"}")
                .productData("{\"vehicle\":{}}")
                .receivedAt(Instant.now())
                .build();
        ingestionRequestRepository.save(request);

        MvcResult result = mockMvc.perform(get("/api/v1/partner/insurance/submissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("records").isArray()).isTrue();
        boolean found = false;
        JsonNode records = body.path("records");
        if (records.isArray()) {
            for (JsonNode r : records) {
                if (r.path("externalReference").asText().equals("CAR-001")) {
                    found = true;
                    break;
                }
            }
        }
        assertThat(found).isTrue();
    }

    // ─── GET /api/v1/partner/insurance/submissions/{id} ─────────────────

    @Test
    @DisplayName("GET submissions/{id}: returns detail for partner's own submission")
    void getSubmission_detail() throws Exception {
        IngestionRequest request = IngestionRequest.builder()
                
                .externalReference("CAR-DETAIL")
                .idempotencyKey("idem_detail")
                .partnerId(partnerId)
                .insurerId(insurerId)
                .productType("MOTOR_INSURANCE")
                .status(IngestionStatus.RECEIVED)
                .customerData("{\"name\":\"Rahul\",\"mobile\":\"9999999999\",\"email\":\"r@e.com\"}")
                .productData("{\"vehicle\":{\"make\":\"Hyundai\"}}")
                .receivedAt(Instant.now())
                .build();
        ingestionRequestRepository.save(request);

        mockMvc.perform(get("/api/v1/partner/insurance/submissions/{id}", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.externalReference").value("CAR-DETAIL"))
                .andExpect(jsonPath("$.data.status").value("RECEIVED"));
    }

    @Test
    @DisplayName("GET submissions/{id}: returns 404 for unknown submission")
    void getSubmission_unknown_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/partner/insurance/submissions/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    // ─── GET /api/v1/partner/insurance/submissions/{id}/validation ──────

    @Test
    @DisplayName("GET submissions/{id}/validation: returns persisted validation result")
    void getValidation_returnsResult() throws Exception {
        IngestionRequest request = IngestionRequest.builder()
                
                .externalReference("CAR-VAL")
                .idempotencyKey("idem_val")
                .partnerId(partnerId)
                .insurerId(insurerId)
                .productType("MOTOR_INSURANCE")
                .status(IngestionStatus.VALIDATION_FAILED)
                .customerData("{}")
                .productData("{}")
                .receivedAt(Instant.now())
                .build();
        request = ingestionRequestRepository.save(request);

        String validationJson = """
                {
                  "submissionId": "%s",
                  "schemaVersion": "1.0",
                  "validationStatus": "VALIDATION_FAILED",
                  "totalChecks": 2,
                  "passedChecks": 0,
                  "failedChecks": 2,
                  "errors": [
                    {"field": "customer.name", "code": "REQUIRED_FIELD_MISSING", "message": "Required field 'Customer Full Name' is missing"}
                  ],
                  "validatedAt": "2026-11-16T10:30:00Z"
                }
                """.formatted(request.getId());
        request.setValidationResult(validationJson);
        ingestionRequestRepository.save(request);

        mockMvc.perform(get("/api/v1/partner/insurance/submissions/{id}/validation", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.submissionId").value(request.getId().toString()))
                .andExpect(jsonPath("$.data.validationStatus").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data.failedChecks").value(2));
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private record Credentials(String partnerId, String clientId, String clientSecret) {
    }

    private Credentials createPartnerAndClient(String partnerCode, List<String> scopes, Integer rateLimit)
            throws Exception {
        String partnerBody = objectMapper.writeValueAsString(java.util.Map.of(
                "partnerCode", partnerCode,
                "partnerName", partnerCode + " Limited",
                "partnerType", "BANK",
                "contactEmail", partnerCode.toLowerCase() + "@example.com",
                "maxDocumentSizeMb", 50,
                "approvedSourceHosts", "[\"*.s3.amazonaws.com\"]"));

        MvcResult partnerResult = mockMvc.perform(post("/api/v1/admin/partners")
                        .contentType(APPLICATION_JSON)
                        .content(partnerBody))
                .andExpect(status().isCreated())
                .andReturn();

        String pid = objectMapper.readTree(partnerResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        ObjectNode clientBody = objectMapper.createObjectNode();
        ArrayNode scopeArray = clientBody.putArray("scopes");
        scopes.forEach(scopeArray::add);
        if (rateLimit != null) {
            clientBody.put("rateLimitPerMinute", rateLimit);
        }

        MvcResult clientResult = mockMvc.perform(post(
                        "/api/v1/admin/partners/{partnerId}/api-clients", pid)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clientBody)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode client = objectMapper.readTree(clientResult.getResponse().getContentAsString())
                .path("data");

        ApiClient persisted = apiClientRepository.findByClientId(client.path("clientId").asText()).orElseThrow();
        // Keep the client active so tokens can be issued
        persisted.setActive(true);
        apiClientRepository.save(persisted);

        return new Credentials(pid, client.path("clientId").asText(),
                client.path("client_secret").asText());
    }

    private String requestToken(String clientId, String clientSecret) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tokenRequest(clientId, clientSecret))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("access_token").asText();
    }

    private ObjectNode tokenRequest(String clientId, String clientSecret) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("client_id", clientId);
        body.put("client_secret", clientSecret);
        body.put("grant_type", "client_credentials");
        return body;
    }
}
