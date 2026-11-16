package com.insurance.integration.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class H2DatabaseProfileTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void servesOperationsConsoleWithoutAuthentication() {
        ResponseEntity<String> page = restTemplate.getForEntity("/", String.class);
        ResponseEntity<String> script = restTemplate.getForEntity("/console.js", String.class);

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("Operations Console", "Auth playground");
        assertThat(script.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(script.getBody()).contains("/api/v1/auth/token", "/api/v1/partner/me");
    }

    @Test
    void healthSwaggerAndStructuredErrorsAreAvailable() throws Exception {
        ResponseEntity<String> health = restTemplate.getForEntity("/api/v1/health", String.class);
        ResponseEntity<String> actuator = restTemplate.getForEntity("/actuator/health", String.class);
        ResponseEntity<String> openApi = restTemplate.getForEntity("/v3/api-docs", String.class);
        ResponseEntity<String> swaggerUi = restTemplate.getForEntity("/swagger-ui/index.html", String.class);
        ResponseEntity<String> notFound = restTemplate.getForEntity(
                "/api/v1/admin/partners/" + UUID.randomUUID(), String.class);

        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(health.getBody()).path("data").path("status").asText())
                .isEqualTo("UP");
        assertThat(actuator.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(actuator.getBody()).path("status").asText()).isEqualTo("UP");
        assertThat(openApi.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(openApi.getBody()).path("openapi").asText()).isNotBlank();
        assertThat(swaggerUi.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode error = objectMapper.readTree(notFound.getBody());
        assertThat(error.path("status").asInt()).isEqualTo(404);
        assertThat(error.path("errorType").asText()).isEqualTo("NOT_FOUND");
    }

    @Test
    void createsAndReadsInsurerThroughLocalApi() {
        String request = """
                {
                  "insurerCode": "POSTMAN_TEST",
                  "insurerName": "Postman Test Insurance",
                  "insurerType": "LIFE",
                  "contactEmail": "api-test@example.com"
                }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> created = restTemplate.postForEntity(
                "/api/v1/admin/insurers", new HttpEntity<>(request, headers), String.class);
        ResponseEntity<String> retrieved = restTemplate.getForEntity(
                "/api/v1/admin/insurers/code/POSTMAN_TEST", String.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retrieved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retrieved.getBody()).contains("POSTMAN_TEST");
    }

    @Test
    void persistsPartnerInsurerMappingThroughLocalApi() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        String partnerCode = "H2_PARTNER_" + suffix;
        String insurerCode = "H2_INSURER_" + suffix;
        String partnerRequest = """
                {
                  "partnerCode": "%s",
                  "partnerName": "H2 Mapping Test Partner",
                  "partnerType": "BANK",
                  "contactEmail": "partner-%s@example.com",
                  "maxDocumentSizeMb": 50
                }
                """.formatted(partnerCode, suffix.toLowerCase());
        String insurerRequest = """
                {
                  "insurerCode": "%s",
                  "insurerName": "H2 Mapping Test Insurer",
                  "insurerType": "GENERAL",
                  "contactEmail": "insurer-%s@example.com"
                }
                """.formatted(insurerCode, suffix.toLowerCase());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> createdPartner = restTemplate.postForEntity(
                "/api/v1/admin/partners", new HttpEntity<>(partnerRequest, headers), String.class);
        assertThat(createdPartner.getStatusCode())
                .as("Partner registration response: %s", createdPartner.getBody())
                .isEqualTo(HttpStatus.CREATED);
        ResponseEntity<String> createdInsurer = restTemplate.postForEntity(
                "/api/v1/admin/insurers", new HttpEntity<>(insurerRequest, headers), String.class);
        String partnerId = objectMapper.readTree(createdPartner.getBody())
                .path("data").path("id").asText();
        String mappingRequest = objectMapper.writeValueAsString(java.util.Map.of(
                "insurerCode", insurerCode,
                "permittedProductTypes", "[\"MOTOR_INSURANCE\"]"));

        ResponseEntity<String> createdMapping = restTemplate.postForEntity(
                "/api/v1/admin/partners/" + partnerId + "/mappings",
                new HttpEntity<>(mappingRequest, headers), String.class);
        ResponseEntity<String> retrievedMappings = restTemplate.getForEntity(
                "/api/v1/admin/partners/" + partnerId + "/mappings", String.class);

        assertThat(createdInsurer.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createdMapping.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retrievedMappings.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode mapping = objectMapper.readTree(retrievedMappings.getBody()).path("data").get(0);
        assertThat(mapping.path("partnerCode").asText()).isEqualTo(partnerCode);
        assertThat(mapping.path("insurerCode").asText()).isEqualTo(insurerCode);
    }

    @Test
    void provisionsClientAndExercisesPartnerAuthenticationEndpoints() throws Exception {
        String partnerRequest = """
                {
                  "partnerCode": "POSTMAN_AUTH_UI",
                  "partnerName": "Postman Auth UI Demo",
                  "partnerType": "BANK",
                  "contactEmail": "auth-ui@example.com",
                  "maxDocumentSizeMb": 50
                }
                """;
        ResponseEntity<String> createdPartner = restTemplate.postForEntity(
                "/api/v1/admin/partners", jsonRequest(partnerRequest), String.class);
        assertThat(createdPartner.getStatusCode())
                .as("Partner registration response: %s", createdPartner.getBody())
                .isEqualTo(HttpStatus.CREATED);
        String partnerId = objectMapper.readTree(createdPartner.getBody()).path("data").path("id").asText();

        ResponseEntity<String> createdClient = restTemplate.postForEntity(
                "/api/v1/admin/partners/" + partnerId + "/api-clients",
                jsonRequest("{\"scopes\":[\"read\",\"write\"]}"), String.class);
        assertThat(createdClient.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode client = objectMapper.readTree(createdClient.getBody()).path("data");
        String clientId = client.path("clientId").asText();
        String clientSecret = client.path("client_secret").asText();

        String tokenRequest = objectMapper.writeValueAsString(java.util.Map.of(
                "grant_type", "client_credentials",
                "client_id", clientId,
                "client_secret", clientSecret));
        ResponseEntity<String> issued = restTemplate.postForEntity(
                "/api/v1/auth/token", jsonRequest(tokenRequest), String.class);
        assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode tokens = objectMapper.readTree(issued.getBody());
        String accessToken = tokens.path("access_token").asText();
        String refreshToken = tokens.path("refresh_token").asText();
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(accessToken);

        ResponseEntity<String> verified = restTemplate.exchange(
                "/api/v1/auth/verify", org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(authHeaders), String.class);
        ResponseEntity<String> profile = restTemplate.exchange(
                "/api/v1/partner/me", org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(authHeaders), String.class);
        ResponseEntity<String> refreshed = restTemplate.postForEntity(
                "/api/v1/auth/refresh",
                jsonRequest(objectMapper.writeValueAsString(java.util.Map.of("refresh_token", refreshToken))),
                String.class);

        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profile.getBody()).contains("POSTMAN_AUTH_UI");
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshed.getBody()).contains("access_token");
    }

    private HttpEntity<String> jsonRequest(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
