package com.insurance.integration.security.dto;

import com.insurance.integration.common.enums.PartnerType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Partner-facing profile returned by {@code GET /api/v1/partner/me}.
 */
@Getter
@Builder
public class PartnerProfileResponse {

    private final UUID partnerId;
    private final String partnerCode;
    private final String partnerName;
    private final PartnerType partnerType;
    private final String contactEmail;
    private final boolean active;
    private final String clientId;
    private final List<String> scopes;
    private final Instant tokenExpiresAt;
}
