package com.insurance.integration.security.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Admin request to issue OAuth 2.0 credentials for a partner.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateApiClientRequest {

    /** Allowed: read, write, admin. Defaults to ["read"]. */
    private List<String> scopes;

    /** Access-token lifetime in seconds (60–86400). Defaults to 3600. */
    @Min(value = 60, message = "tokenExpirySeconds must be at least 60")
    @Max(value = 86400, message = "tokenExpirySeconds must not exceed 86400")
    private Integer tokenExpirySeconds;

    /** Requests allowed per 60-second window. Defaults to the platform limit. */
    @Min(value = 1, message = "rateLimitPerMinute must be at least 1")
    @Max(value = 10000, message = "rateLimitPerMinute must not exceed 10000")
    private Integer rateLimitPerMinute;
}
