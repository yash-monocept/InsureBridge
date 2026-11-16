package com.insurance.integration.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.insurance.integration.partner.repository.PartnerInsurerMappingRepository;
import com.insurance.integration.partner.repository.PartnerRepository;
import com.insurance.integration.security.entity.ApiClient;
import com.insurance.integration.security.jwt.JwtService;
import com.insurance.integration.security.repository.ApiClientRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for Module 2 partner authentication:
 * OAuth 2.0 Client Credentials, JWT validation, scopes, rotation, rate limiting
 * and partner deactivation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Module 2 — Partner Authentication Integration Tests")
class PartnerAuthenticationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private PartnerInsurerMappingRepository mappingRepository;

    @Autowired
    private ApiClientRepository apiClientRepository;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void cleanDatabase() {
        apiClientRepository.deleteAll();
        mappingRepository.deleteAll();
        partnerRepository.deleteAll();
    }

    // ─── OAuth 2.0 token issuance ────────────────────────────────────────────

    @Test
    @DisplayName("valid client credentials issue an RS256 JWT with required claims")
    void validCredentialsIssueJwt() throws Exception {
        Credentials credentials = createPartnerAndClient("AUTH_BANK", List.of("read", "write"), null);

        JsonNode token = requestToken(credentials);

        assertThat(token.path("access_token").asText()).isNotBlank();
        assertThat(token.path("token_type").asText()).isEqualTo("Bearer");
        assertThat(token.path("expires_in").asInt()).isEqualTo(3600);
        assertThat(token.path("refresh_token").asText()).isNotBlank();
        assertThat(token.path("scopes").toString()).contains("read");

        Claims claims = jwtService.parseClaims(token.path("access_token").asText());
        assertThat(claims.get(JwtService.CLAIM_PARTNER_CODE, String.class)).isEqualTo("AUTH_BANK");
        assertThat(claims.get(JwtService.CLAIM_CLIENT_ID, String.class)).isEqualTo(credentials.clientId());
        assertThat(claims.get(JwtService.CLAIM_SCOPES, List.class)).contains("read", "write");
        assertThat(claims.getExpiration()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("form-encoded client credentials are accepted")
    void formEncodedCredentialsIssueJwt() throws Exception {
        Credentials credentials = createPartnerAndClient("FORM_BANK", List.of("read"), null);

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", credentials.clientId())
                        .param("client_secret", credentials.clientSecret()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty());
    }

    @Test
    @DisplayName("invalid client secret is rejected with 401")
    void invalidSecretRejected() throws Exception {
        Credentials credentials = createPartnerAndClient("BAD_SECRET_BANK", List.of("read"), null);
        String invalidSecret = "invalid-secret-canary";

        MvcResult result = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tokenRequest(
                                credentials.clientId(), invalidSecret))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorType").value("AUTHENTICATION_FAILED"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(invalidSecret);
    }

    @Test
    @DisplayName("unknown client id is rejected with 401")
    void unknownClientRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tokenRequest("cli_does_not_exist", "secret"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("deactivated partner cannot authenticate (403)")
    void inactivePartnerCannotAuthenticate() throws Exception {
        Credentials credentials = createPartnerAndClient("INACTIVE_BANK", List.of("read"), null);

        mockMvc.perform(put("/api/v1/admin/partners/{id}/deactivate", credentials.partnerId()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tokenRequest(
                                credentials.clientId(), credentials.clientSecret()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorType").value("PARTNER_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("deactivated API client cannot authenticate (401)")
    void inactiveClientCannotAuthenticate() throws Exception {
        Credentials credentials = createPartnerAndClient("CLIENT_OFF_BANK", List.of("read"), null);

        mockMvc.perform(put("/api/v1/admin/partners/{partnerId}/api-clients/{clientId}/deactivate",
                        credentials.partnerId(), credentials.clientId()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tokenRequest(
                                credentials.clientId(), credentials.clientSecret()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unsupported grant type is rejected with 401")
    void unsupportedGrantTypeRejected() throws Exception {
        Credentials credentials = createPartnerAndClient("GRANT_BANK", List.of("read"), null);

        ObjectNode body = tokenRequest(credentials.clientId(), credentials.clientSecret());
        body.put("grant_type", "password");

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    // ─── JWT filter on partner endpoints ─────────────────────────────────────

    @Test
    @DisplayName("partner endpoint accepts a valid token and returns the partner profile")
    void partnerMeWithValidToken() throws Exception {
        Credentials credentials = createPartnerAndClient("PROFILE_BANK", List.of("read"), null);
        String accessToken = requestToken(credentials).path("access_token").asText();

        mockMvc.perform(get("/api/v1/partner/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.partnerCode").value("PROFILE_BANK"))
                .andExpect(jsonPath("$.data.clientId").value(credentials.clientId()))
                .andExpect(jsonPath("$.data.scopes[0]").value("read"));
    }

    @Test
    @DisplayName("partner endpoint rejects a missing token with 401")
    void partnerMeWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/partner/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"partner-api\""))
                .andExpect(jsonPath("$.errorType").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("partner endpoint rejects a malformed token with 401")
    void partnerMeWithMalformedToken() throws Exception {
        mockMvc.perform(get("/api/v1/partner/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorType").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("partner endpoint rejects malformed authorization header formats")
    void partnerMeWithMalformedAuthorizationHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/partner/me")
                        .header(HttpHeaders.AUTHORIZATION, "Basic credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorType").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get("/api/v1/partner/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorType").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("partner endpoint rejects an expired access token with 401")
    void partnerMeWithExpiredToken() throws Exception {
        Credentials credentials = createPartnerAndClient("EXPIRED_TOKEN_BANK", List.of("read"), null);
        ApiClient client = apiClientRepository.findByClientId(credentials.clientId()).orElseThrow();
        var partner = partnerRepository.findById(java.util.UUID.fromString(credentials.partnerId()))
                .orElseThrow();
        String expiredToken = jwtService.buildToken(client, partner,
                Instant.now().minusSeconds(60), JwtService.TOKEN_TYPE_ACCESS, List.of("read"));

        mockMvc.perform(get("/api/v1/partner/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("error=\"invalid_token\"")))
                .andExpect(jsonPath("$.errorType").value("TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("partner endpoint rejects a token with a tampered signature")
    void partnerMeWithTamperedSignature() throws Exception {
        Credentials credentials = createPartnerAndClient("TAMPERED_TOKEN_BANK", List.of("read"), null);
        String[] parts = requestToken(credentials).path("access_token").asText().split("\\.");
        String signature = parts[2];
        parts[2] = (signature.startsWith("A") ? "B" : "A")
                + signature.substring(1);

        mockMvc.perform(get("/api/v1/partner/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + String.join(".", parts)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorType").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("a refresh token cannot be used as an access token")
    void refreshTokenCannotAccessPartnerApi() throws Exception {
        Credentials credentials = createPartnerAndClient("REFRESH_GUARD_BANK", List.of("read"), null);
        String refreshToken = requestToken(credentials).path("refresh_token").asText();

        mockMvc.perform(get("/api/v1/partner/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorType").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("deactivating a partner immediately blocks an existing token")
    void partnerDeactivationBlocksExistingToken() throws Exception {
        Credentials credentials = createPartnerAndClient("REVOKE_BANK", List.of("read"), null);
        String accessToken = requestToken(credentials).path("access_token").asText();

        mockMvc.perform(put("/api/v1/admin/partners/{id}/deactivate", credentials.partnerId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/partner/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorType").value("PARTNER_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("missing scope is rejected with 403")
    void insufficientScopeRejected() throws Exception {
        Credentials credentials = createPartnerAndClient("WRITE_ONLY_BANK", List.of("write"), null);
        String accessToken = requestToken(credentials).path("access_token").asText();

        mockMvc.perform(get("/api/v1/partner/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        containsString("error=\"insufficient_scope\"")))
                .andExpect(jsonPath("$.errorType").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("token responses are not cacheable (RFC 6749 §5.1)")
    void tokenResponsesAreNotCacheable() throws Exception {
        Credentials credentials = createPartnerAndClient("CACHE_BANK", List.of("read"), null);

        MvcResult tokenResult = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                tokenRequest(credentials.clientId(), credentials.clientSecret()))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andReturn();

        String refreshToken = objectMapper.readTree(tokenResult.getResponse().getContentAsString())
                .path("refresh_token").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("refresh_token", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"));
    }

    // ─── Token verification and refresh ──────────────────────────────────────

    @Test
    @DisplayName("verify endpoint returns identity claims for a valid access token")
    void verifyReturnsClaims() throws Exception {
        Credentials credentials = createPartnerAndClient("VERIFY_BANK", List.of("read"), null);
        String accessToken = requestToken(credentials).path("access_token").asText();

        mockMvc.perform(get("/api/v1/auth/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partner_code").value("VERIFY_BANK"))
                .andExpect(jsonPath("$.client_id").value(credentials.clientId()))
                .andExpect(jsonPath("$.scopes[0]").value("read"))
                .andExpect(jsonPath("$.expires_at").isNotEmpty());
    }

    @Test
    @DisplayName("verify endpoint rejects a missing token with 401")
    void verifyWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/verify"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refresh token exchanges for a new access token")
    void refreshIssuesNewAccessToken() throws Exception {
        Credentials credentials = createPartnerAndClient("REFRESH_BANK", List.of("read"), null);
        JsonNode first = requestToken(credentials);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("refresh_token", first.path("refresh_token").asText()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andReturn();

        String refreshed = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("access_token").asText();
        assertThat(refreshed).isNotEqualTo(first.path("access_token").asText());

        mockMvc.perform(get("/api/v1/partner/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshed))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an access token cannot be used as a refresh token")
    void accessTokenCannotRefresh() throws Exception {
        Credentials credentials = createPartnerAndClient("NO_REFRESH_BANK", List.of("read"), null);
        String accessToken = requestToken(credentials).path("access_token").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("refresh_token", accessToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unknown API client scopes are rejected")
    void unsupportedScopesRejected() throws Exception {
        Credentials credentials = createPartnerAndClient("INVALID_SCOPE_BANK", List.of("read"), null);
        ObjectNode invalidClient = objectMapper.createObjectNode();
        invalidClient.putArray("scopes").add("superuser");

        mockMvc.perform(post("/api/v1/admin/partners/{partnerId}/api-clients", credentials.partnerId())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidClient)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorType").value("BUSINESS_VALIDATION_ERROR"));
    }

    // ─── Secret rotation ─────────────────────────────────────────────────────

    @Test
    @DisplayName("rotation accepts old and new secrets during the grace window")
    void rotationGraceWindow() throws Exception {
        Credentials credentials = createPartnerAndClient("ROTATE_BANK", List.of("read"), null);

        MvcResult rotated = mockMvc.perform(put(
                        "/api/v1/admin/partners/{partnerId}/api-clients/{clientId}/rotate",
                        credentials.partnerId(), credentials.clientId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.client_secret").isNotEmpty())
                .andExpect(jsonPath("$.data.previousSecretValidUntil").isNotEmpty())
                .andReturn();

        String newSecret = objectMapper.readTree(rotated.getResponse().getContentAsString())
                .path("data").path("client_secret").asText();

        requestToken(credentials.clientId(), credentials.clientSecret());
        requestToken(credentials.clientId(), newSecret);

        // Force the grace window closed and confirm only the new secret works.
        ApiClient client = apiClientRepository.findByClientId(credentials.clientId()).orElseThrow();
        client.setLastRotatedAt(Instant.now().minusSeconds(25 * 3600));
        apiClientRepository.save(client);

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tokenRequest(
                                credentials.clientId(), credentials.clientSecret()))))
                .andExpect(status().isUnauthorized());

        requestToken(credentials.clientId(), newSecret);
    }

    // ─── Rate limiting ───────────────────────────────────────────────────────

    @Test
    @DisplayName("exceeding rate_limit_per_minute returns 429 with Retry-After")
    void rateLimitExceeded() throws Exception {
        Credentials credentials = createPartnerAndClient("RATE_BANK", List.of("read"), 2);
        String accessToken = requestToken(credentials).path("access_token").asText();

        mockMvc.perform(get("/api/v1/partner/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "2"));

        mockMvc.perform(get("/api/v1/partner/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/partner/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorType").value("RATE_LIMIT_EXCEEDED"));
    }

    // ─── Credential administration ───────────────────────────────────────────

    @Test
    @DisplayName("admin listing never exposes the secret or its hash")
    void listingAfterCreateSucceeds() throws Exception {
        Credentials credentials = createPartnerAndClient("LIST_BANK", List.of("read"), null);
        ApiClient persistedClient = apiClientRepository.findByClientId(credentials.clientId()).orElseThrow();
        assertThat(persistedClient.getClientSecretHash())
                .isNotEqualTo(credentials.clientSecret())
                .startsWith("$2");

        MvcResult result = mockMvc.perform(get(
                        "/api/v1/admin/partners/{partnerId}/api-clients", credentials.partnerId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].clientId").value(credentials.clientId()))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("clientSecret");
        assertThat(body).doesNotContain("client_secret");
        assertThat(body).doesNotContain("$2a$");
    }

    @Test
    @DisplayName("deactivating a client blocks refresh but does not revoke an existing access token")
    void clientDeactivationLeavesIssuedAccessTokenValidUntilExpiry() throws Exception {
        Credentials credentials = createPartnerAndClient("CLIENT_REVOCATION_BANK", List.of("read"), null);
        JsonNode tokens = requestToken(credentials);
        String accessToken = tokens.path("access_token").asText();
        String refreshToken = tokens.path("refresh_token").asText();

        mockMvc.perform(put("/api/v1/admin/partners/{partnerId}/api-clients/{clientId}/deactivate",
                        credentials.partnerId(), credentials.clientId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/partner/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.partnerCode").value("CLIENT_REVOCATION_BANK"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("refresh_token", refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorType").value("AUTHENTICATION_FAILED"));
    }

    @Test
    @DisplayName("creating credentials for an unknown partner returns 404")
    void createForUnknownPartner() throws Exception {
        mockMvc.perform(post("/api/v1/admin/partners/{id}/api-clients",
                        java.util.UUID.randomUUID())
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private record Credentials(String partnerId, String clientId, String clientSecret) {
    }

    private Credentials createPartnerAndClient(String partnerCode, List<String> scopes, Integer rateLimit)
            throws Exception {

        String partnerBody = objectMapper.writeValueAsString(java.util.Map.of(
                "partnerCode", partnerCode,
                "partnerName", partnerCode + " Limited",
                "partnerType", "BANK",
                "contactEmail", partnerCode.toLowerCase() + "@example.com",
                "maxDocumentSizeMb", 50));

        MvcResult partnerResult = mockMvc.perform(post("/api/v1/admin/partners")
                        .contentType(APPLICATION_JSON)
                        .content(partnerBody))
                .andExpect(status().isCreated())
                .andReturn();

        String partnerId = objectMapper.readTree(partnerResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        ObjectNode clientBody = objectMapper.createObjectNode();
        ArrayNode scopeArray = clientBody.putArray("scopes");
        scopes.forEach(scopeArray::add);
        if (rateLimit != null) {
            clientBody.put("rateLimitPerMinute", rateLimit);
        }

        MvcResult clientResult = mockMvc.perform(post(
                        "/api/v1/admin/partners/{partnerId}/api-clients", partnerId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clientBody)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode client = objectMapper.readTree(clientResult.getResponse().getContentAsString())
                .path("data");

        return new Credentials(partnerId, client.path("clientId").asText(),
                client.path("client_secret").asText());
    }

    private JsonNode requestToken(Credentials credentials) throws Exception {
        return requestToken(credentials.clientId(), credentials.clientSecret());
    }

    private JsonNode requestToken(String clientId, String clientSecret) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tokenRequest(clientId, clientSecret))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private ObjectNode tokenRequest(String clientId, String clientSecret) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("client_id", clientId);
        body.put("client_secret", clientSecret);
        body.put("grant_type", "client_credentials");
        return body;
    }
}
