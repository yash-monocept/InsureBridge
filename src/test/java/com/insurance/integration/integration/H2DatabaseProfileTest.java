package com.insurance.integration.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class H2DatabaseProfileTest {

    @Autowired
    private TestRestTemplate restTemplate;

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
}
