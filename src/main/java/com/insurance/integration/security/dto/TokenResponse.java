package com.insurance.integration.security.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * OAuth 2.0 token response. Follows RFC 6749 field naming, with the
 * partner-facing scopes and a refresh token included.
 */
@Getter
@Builder
public class TokenResponse {

    @JsonProperty("access_token")
    private final String accessToken;

    @JsonProperty("token_type")
    private final String tokenType;

    @JsonProperty("expires_in")
    private final long expiresIn;

    @JsonProperty("refresh_token")
    private final String refreshToken;

    @JsonProperty("refresh_expires_in")
    private final long refreshExpiresIn;

    @JsonProperty("scopes")
    private final List<String> scopes;
}
