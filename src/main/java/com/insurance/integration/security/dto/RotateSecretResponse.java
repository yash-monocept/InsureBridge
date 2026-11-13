package com.insurance.integration.security.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Response returned when a client secret is rotated. The new plaintext secret
 * is shown once; the previous secret remains valid until
 * {@code previousSecretValidUntil}.
 */
@Getter
@Builder
public class RotateSecretResponse {

    private final String clientId;

    @JsonProperty("client_secret")
    private final String clientSecret;

    private final Instant lastRotatedAt;
    private final Instant previousSecretValidUntil;
}
