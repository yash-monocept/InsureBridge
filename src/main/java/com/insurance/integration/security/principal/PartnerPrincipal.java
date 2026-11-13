package com.insurance.integration.security.principal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Authenticated partner identity extracted from a validated JWT and stored in
 * the Spring Security context for the duration of a request.
 */
public record PartnerPrincipal(
        UUID partnerId,
        String partnerCode,
        String clientId,
        List<String> scopes,
        int rateLimitPerMinute,
        Instant expiresAt) {

    public boolean hasScope(String scope) {
        return scopes != null && scopes.contains(scope);
    }
}
