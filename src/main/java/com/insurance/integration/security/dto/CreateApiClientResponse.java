package com.insurance.integration.security.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response returned once when an API client is created. This is the only time
 * the plaintext {@code clientSecret} is available — it is never stored.
 */
@Getter
@Builder
public class CreateApiClientResponse {

    private final UUID id;
    private final UUID partnerId;
    private final String clientId;

    @JsonProperty("client_secret")
    private final String clientSecret;

    private final List<String> scopes;
    private final int tokenExpirySeconds;
    private final int rateLimitPerMinute;
    private final boolean active;
    private final Instant createdAt;
}
