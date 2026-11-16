package com.insurance.integration.security.controller;

import com.insurance.integration.common.exception.PartnerAuthenticationException;
import com.insurance.integration.security.dto.RefreshTokenRequest;
import com.insurance.integration.security.dto.TokenRequest;
import com.insurance.integration.security.dto.TokenResponse;
import com.insurance.integration.security.dto.VerifyTokenResponse;
import com.insurance.integration.security.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth 2.0 Client Credentials endpoints for partner authentication.
 *
 * <p>Public endpoints (see SecurityConfig). Successful token responses follow
 * RFC 6749 field naming.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "OAuth 2.0 Client Credentials, token refresh and verification")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CACHE_CONTROL_NO_STORE = "no-store";
    private static final String PRAGMA_NO_CACHE = "no-cache";

    private final AuthenticationService authenticationService;

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtain an access token (JSON)",
            description = "Authenticates partner API credentials using the OAuth 2.0 Client Credentials flow. "
                    + "Returns a short-lived RS256 JWT access token and a refresh token.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token issued"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Malformed request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid client credentials"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Partner is not active")
    })
    public ResponseEntity<TokenResponse> token(@Valid @RequestBody TokenRequest request) {
        TokenResponse response = authenticationService.issueTokens(
                request.getClientId(), request.getClientSecret(), request.getGrantType());
        return tokenResponse(response);
    }

    /**
     * Standards-compliant form-encoded variant
     * ({@code application/x-www-form-urlencoded}) used by OAuth clients and
     * Postman's OAuth 2.0 helper.
     */
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "Obtain an access token (form-encoded)",
            description = "OAuth 2.0 Client Credentials flow using application/x-www-form-urlencoded parameters.")
    public ResponseEntity<TokenResponse> tokenForm(
            @RequestParam("client_id") String clientId,
            @RequestParam("client_secret") String clientSecret,
            @RequestParam(value = "grant_type", required = false) String grantType) {

        TokenResponse response = authenticationService.issueTokens(clientId, clientSecret, grantType);
        return tokenResponse(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new token pair")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return tokenResponse(authenticationService.refresh(request.getRefreshToken()));
    }

    @GetMapping("/verify")
    @Operation(summary = "Verify an access token and inspect its claims",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<VerifyTokenResponse> verify(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new PartnerAuthenticationException("Authorization header with a Bearer token is required");
        }
        return ResponseEntity.ok(authenticationService.verify(authorization.substring(BEARER_PREFIX.length()).trim()));
    }

    /**
     * RFC 6749 §5.1 requires token responses to prevent caching.
     */
    private ResponseEntity<TokenResponse> tokenResponse(TokenResponse response) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_NO_STORE)
                .header(HttpHeaders.PRAGMA, PRAGMA_NO_CACHE)
                .body(response);
    }
}
