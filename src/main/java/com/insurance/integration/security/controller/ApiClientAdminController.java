package com.insurance.integration.security.controller;

import com.insurance.integration.common.response.ApiResponse;
import com.insurance.integration.security.dto.ApiClientResponse;
import com.insurance.integration.security.dto.CreateApiClientRequest;
import com.insurance.integration.security.dto.CreateApiClientResponse;
import com.insurance.integration.security.dto.RotateSecretResponse;
import com.insurance.integration.security.service.ClientCredentialsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin APIs for managing partner OAuth 2.0 credentials.
 *
 * <p>These endpoints are part of the Module 2 credential lifecycle. The
 * plaintext client secret is returned only once — at creation or rotation.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/partners/{partnerId}/api-clients")
@RequiredArgsConstructor
@Tag(name = "API Client Management", description = "Admin APIs for issuing and rotating partner OAuth 2.0 credentials")
public class ApiClientAdminController {

    private final ClientCredentialsService clientCredentialsService;

    @PostMapping
    @Operation(summary = "Create API credentials for a partner",
            description = "Generates a client_id and a client_secret. The secret is returned once and stored only as a BCrypt hash.")
    public ResponseEntity<ApiResponse<CreateApiClientResponse>> create(
            @PathVariable UUID partnerId,
            @Valid @RequestBody(required = false) CreateApiClientRequest request) {

        CreateApiClientResponse response = clientCredentialsService.createClient(partnerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("API client created", response));
    }

    @GetMapping
    @Operation(summary = "List API clients for a partner",
            description = "Returns credential metadata. Secrets and hashes are never exposed.")
    public ResponseEntity<ApiResponse<List<ApiClientResponse>>> list(@PathVariable UUID partnerId) {
        return ResponseEntity.ok(ApiResponse.success(
                "API clients retrieved", clientCredentialsService.listClients(partnerId)));
    }

    @PutMapping("/{clientId}/rotate")
    @Operation(summary = "Rotate a client secret",
            description = "Issues a new secret; the previous secret remains valid for 24 hours.")
    public ResponseEntity<ApiResponse<RotateSecretResponse>> rotate(
            @PathVariable UUID partnerId,
            @PathVariable String clientId) {

        RotateSecretResponse response = clientCredentialsService.rotateSecret(partnerId, clientId);
        return ResponseEntity.ok(ApiResponse.success("Client secret rotated", response));
    }

    @PutMapping("/{clientId}/activate")
    @Operation(summary = "Activate an API client")
    public ResponseEntity<ApiResponse<ApiClientResponse>> activate(
            @PathVariable UUID partnerId,
            @PathVariable String clientId) {

        return ResponseEntity.ok(ApiResponse.success(
                "API client activated", clientCredentialsService.setActive(partnerId, clientId, true)));
    }

    @PutMapping("/{clientId}/deactivate")
    @Operation(summary = "Deactivate an API client",
            description = "Immediately prevents the client from obtaining new tokens.")
    public ResponseEntity<ApiResponse<ApiClientResponse>> deactivate(
            @PathVariable UUID partnerId,
            @PathVariable String clientId) {

        return ResponseEntity.ok(ApiResponse.success(
                "API client deactivated", clientCredentialsService.setActive(partnerId, clientId, false)));
    }
}
