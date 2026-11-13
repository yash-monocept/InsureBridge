package com.insurance.integration.security.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin view of an API client. Never contains the secret or its hash.
 */
@Getter
@Builder
public class ApiClientResponse {

    private final UUID id;
    private final UUID partnerId;
    private final String clientId;
    private final List<String> scopes;
    private final int tokenExpirySeconds;
    private final int rateLimitPerMinute;
    private final boolean active;
    private final Instant lastRotatedAt;
    private final Instant createdAt;
    private final Instant updatedAt;
}
