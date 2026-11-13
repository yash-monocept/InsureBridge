package com.insurance.integration.security.controller;

import com.insurance.integration.common.exception.ResourceNotFoundException;
import com.insurance.integration.common.response.ApiResponse;
import com.insurance.integration.partner.entity.Partner;
import com.insurance.integration.partner.repository.PartnerRepository;
import com.insurance.integration.security.dto.PartnerProfileResponse;
import com.insurance.integration.security.principal.PartnerPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Partner-facing identity endpoint, used to verify that JWT authentication and
 * scope enforcement work end to end. Module 3 adds the ingestion endpoints
 * under the same prefix.
 */
@RestController
@RequestMapping("/api/v1/partner")
@RequiredArgsConstructor
@Tag(name = "Partner API", description = "Authenticated partner-facing endpoints")
@SecurityRequirement(name = "bearerAuth")
public class PartnerProfileController {

    private final PartnerRepository partnerRepository;

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('SCOPE_read') or hasAuthority('SCOPE_admin')")
    @Operation(summary = "Get the authenticated partner's profile",
            description = "Requires a valid partner JWT with the 'read' (or 'admin') scope.")
    public ResponseEntity<ApiResponse<PartnerProfileResponse>> me(
            @AuthenticationPrincipal PartnerPrincipal principal) {

        Partner partner = partnerRepository.findById(principal.partnerId())
                .orElseThrow(() -> new ResourceNotFoundException("Partner", principal.partnerId().toString()));

        PartnerProfileResponse response = PartnerProfileResponse.builder()
                .partnerId(partner.getId())
                .partnerCode(partner.getPartnerCode())
                .partnerName(partner.getPartnerName())
                .partnerType(partner.getPartnerType())
                .contactEmail(partner.getContactEmail())
                .active(partner.isActive())
                .clientId(principal.clientId())
                .scopes(principal.scopes())
                .tokenExpiresAt(principal.expiresAt())
                .build();

        return ResponseEntity.ok(ApiResponse.success("Partner profile retrieved", response));
    }
}
