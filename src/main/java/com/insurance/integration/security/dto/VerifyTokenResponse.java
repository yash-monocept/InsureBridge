package com.insurance.integration.security.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response for {@code GET /api/v1/auth/verify} — echoes the identity carried
 * by a valid access token.
 */
@Getter
@Builder
public class VerifyTokenResponse {

    @JsonProperty("partner_id")
    private final UUID partnerId;

    @JsonProperty("partner_code")
    private final String partnerCode;

    @JsonProperty("client_id")
    private final String clientId;

    @JsonProperty("scopes")
    private final List<String> scopes;

    @JsonProperty("issued_at")
    private final Instant issuedAt;

    @JsonProperty("expires_at")
    private final Instant expiresAt;
}
