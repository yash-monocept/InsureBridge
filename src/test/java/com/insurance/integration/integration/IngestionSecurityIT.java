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
import com.insurance.integration.partner.entity.Partner;
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
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security tests for Modules 3/4: authentication, authorisation, and partner isolation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@DisplayName("Modules 3-4 — Security Integration Tests")
class IngestionSecurityIT {

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
    private IngestionRequestRepository ingestionRequestRepository;

    @Autowired
    private IngestionDocumentRepository ingestionDocumentRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private com.insurance.integration.schema.repository.ProductSchemaRepository schemaRepository;

    private String tokenA;
    private String tokenB;
    private UUID partnerAId;
    private UUID partnerBId;
    private UUID insurerId;

    @BeforeEach
    void setUp() throws Exception {
        apiClientRepository.deleteAll();
        mappingRepository.deleteAll();
        ingestionDocumentRepository.deleteAll();
        ingestionRequestRepository.deleteAll();
        partnerRepository.deleteAll();
        insurerRepository.deleteAll();

        Insurer insurer = insurerRepository.save(Insurer.builder()
                .insurerCode("MAX_LIFE")
                .insurerName("Max Life Insurance")
                .insurerType(com.insurance.integration.common.enums.InsurerType.LIFE)
                .active(true)
                .build());
        insurerId = insurer.getId();

        tokenA = provisionPartner("PARTNER_A", List.of("read", "write"));
        tokenB = provisionPartner("PARTNER_B", List.of("read"));

        Partner partnerA = partnerRepository.findByPartnerCode("PARTNER_A").orElseThrow();
        Partner partnerB = partnerRepository.findByPartnerCode("PARTNER_B").orElseThrow();
        mappingRepository.save(PartnerInsurerMapping.builder()
                .partner(partnerA).insurer(insurer).active(true).build());
        mappingRepository.save(PartnerInsurerMapping.builder()
                .partner(partnerB).insurer(insurer).active(true)
                .permittedProductTypes("[\"MOTOR_INSURANCE\"]").build());

        schemaRepository.save(com.insurance.integration.schema.entity.ProductSchema.builder()
                .productType("MOTOR_INSURANCE")
                .schemaVersion("1.0")
                .schemaName("Motor Insurance Schema v1.0")
                .active(true)
                .build());
        partnerAId = partnerRepository.findByPartnerCode("PARTNER_A").orElseThrow().getId();
        partnerBId = partnerRepository.findByPartnerCode("PARTNER_B").orElseThrow().getId();
    }

    @Test
    @DisplayName("partner endpoint rejects missing bearer token with 401")
    void missingToken_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/partner/insurance/submissions")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("partner endpoint rejects invalid token with 401")
    void invalidToken_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/partner/insurance/submissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("partner cannot list another partner's submissions (isolation)")
    void partnerIsolation_crossPartnerBlocked() throws Exception {
        String body = """
                {
                  "partnerReferenceId": "CAR-A",
                  "insurerCode": "MAX_LIFE",
                  "productCode": "MOTOR_INSURANCE",
                  "idempotencyKey": "idem_a",
                  "customer": {"name":"A","mobile":"9999999999","email":"a@b.c"},
                  "applicationData": {},
                  "documents": [
                    {"documentType":"IDENTITY_PROOF","fileName":"a.pdf","contentType":"application/pdf",
                     "sourceUrl":"https://bucket.s3.amazonaws.com/a.pdf"}
                  ]
                }""";
        mockMvc.perform(post("/api/v1/partner/insurance/submissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        String listB = mockMvc.perform(get("/api/v1/partner/insurance/submissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode records = objectMapper.readTree(listB).path("records");
        boolean found = false;
        if (records.isArray()) {
            for (JsonNode r : records) {
                if (r.path("externalReference").asText().equals("CAR-A")) {
                    found = true;
                    break;
                }
            }
        }
        assertThat(found).isFalse();
    }

    @Test
    @DisplayName("partner cannot retrieve another partner's submission by id")
    void crossPartnerGetBlocked() throws Exception {
        IngestionRequest request = IngestionRequest.builder()
                .externalReference("CAR-OWNED-BY-A")
                .idempotencyKey("idem_owned_by_a")
                .partnerId(partnerAId)
                .insurerId(insurerId)
                .productType("MOTOR_INSURANCE")
                .status(IngestionStatus.RECEIVED)
                .customerData("{}")
                .productData("{}")
                .receivedAt(Instant.now())
                .build();
        ingestionRequestRepository.save(request);

        mockMvc.perform(get("/api/v1/partner/insurance/submissions/{id}", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("partner with only read scope cannot submit (write required)")
    void readOnlyPartnerCannotSubmit_rejected() throws Exception {
        String body = """
                {
                  "partnerReferenceId": "CAR-RO",
                  "insurerCode": "MAX_LIFE",
                  "productCode": "MOTOR_INSURANCE",
                  "idempotencyKey": "idem_ro",
                  "customer": {"name":"RO","mobile":"9999999999","email":"r@b.c"},
                  "applicationData": {},
                  "documents": [
                    {"documentType":"IDENTITY_PROOF","fileName":"r.pdf","contentType":"application/pdf",
                     "sourceUrl":"https://bucket.s3.amazonaws.com/r.pdf"}
                  ]
                }""";
        mockMvc.perform(post("/api/v1/partner/insurance/submissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("expired token is rejected with 401")
    void expiredToken_rejected() throws Exception {
        ApiClient client = apiClientRepository.findAll().stream().findFirst().orElseThrow();
        var partner = partnerRepository.findById(partnerAId).orElseThrow();
        String expired = jwtService.buildToken(client, partner,
                Instant.now().minusSeconds(60), JwtService.TOKEN_TYPE_ACCESS, List.of("read", "write"));

        mockMvc.perform(get("/api/v1/partner/insurance/submissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private String provisionPartner(String partnerCode, List<String> scopes) throws Exception {
        String partnerBody = objectMapper.writeValueAsString(java.util.Map.of(
                "partnerCode", partnerCode,
                "partnerName", partnerCode + " Limited",
                "partnerType", "BANK",
                "contactEmail", partnerCode.toLowerCase() + "@example.com",
                "maxDocumentSizeMb", 50,
                "approvedSourceHosts", "[\"*.s3.amazonaws.com\"]"));

        String pid = objectMapper.readTree(mockMvc.perform(post("/api/v1/admin/partners")
                        .contentType(APPLICATION_JSON).content(partnerBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("id").asText();

        ObjectNode clientBody = objectMapper.createObjectNode();
        ArrayNode scopeArray = clientBody.putArray("scopes");
        scopes.forEach(scopeArray::add);

        MvcResult clientResult = mockMvc.perform(post(
                        "/api/v1/admin/partners/{partnerId}/api-clients", pid)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clientBody)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode client = objectMapper.readTree(clientResult.getResponse().getContentAsString())
                .path("data");
        String clientId = client.path("clientId").asText();
        String clientSecret = client.path("client_secret").asText();

        ObjectNode tokenReq = objectMapper.createObjectNode();
        tokenReq.put("client_id", clientId);
        tokenReq.put("client_secret", clientSecret);
        tokenReq.put("grant_type", "client_credentials");

        String accessToken = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tokenReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("access_token").asText();

        return accessToken;
    }
}
