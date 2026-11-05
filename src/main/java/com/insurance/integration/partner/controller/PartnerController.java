package com.insurance.integration.partner.controller;

import com.insurance.integration.common.enums.PartnerType;
import com.insurance.integration.common.response.ApiResponse;
import com.insurance.integration.common.response.PagedResponse;
import com.insurance.integration.partner.dto.CreateMappingRequest;
import com.insurance.integration.partner.dto.CreatePartnerRequest;
import com.insurance.integration.partner.dto.MappingResponse;
import com.insurance.integration.partner.dto.PartnerResponse;
import com.insurance.integration.partner.dto.UpdatePartnerRequest;
import com.insurance.integration.partner.service.PartnerMappingService;
import com.insurance.integration.partner.service.PartnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin REST API for partner management.
 *
 * All endpoints in this controller require admin authentication.
 * Partners cannot access these endpoints.
 * Security enforcement is handled by Spring Security + JWT filter (Module 2).
 */
@RestController
@RequestMapping("/api/v1/admin/partners")
@RequiredArgsConstructor
@Tag(name = "Partner Management", description = "Admin APIs for managing distribution partners and their insurer mappings")
public class PartnerController {

    private final PartnerService partnerService;
    private final PartnerMappingService partnerMappingService;

    // ─── Partner CRUD ────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Register a new distribution partner",
               description = "Creates a new partner record. Partners cannot self-register — only admins can create partners.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Partner registered successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Partner code already exists")
    })
    public ResponseEntity<ApiResponse<PartnerResponse>> registerPartner(
            @Valid @RequestBody CreatePartnerRequest request) {

        // TODO Module 2: extract admin username from JWT SecurityContext
        String createdBy = "admin";
        PartnerResponse response = partnerService.registerPartner(request, createdBy);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Partner registered successfully", response));
    }

    @PutMapping("/{partnerId}")
    @Operation(summary = "Update an existing partner")
    public ResponseEntity<ApiResponse<PartnerResponse>> updatePartner(
            @PathVariable UUID partnerId,
            @Valid @RequestBody UpdatePartnerRequest request) {

        PartnerResponse response = partnerService.updatePartner(partnerId, request);
        return ResponseEntity.ok(ApiResponse.success("Partner updated successfully", response));
    }

    @PutMapping("/{partnerId}/activate")
    @Operation(summary = "Activate a partner", description = "Re-activates a previously deactivated partner.")
    public ResponseEntity<ApiResponse<PartnerResponse>> activatePartner(@PathVariable UUID partnerId) {
        PartnerResponse response = partnerService.activatePartner(partnerId);
        return ResponseEntity.ok(ApiResponse.success("Partner activated", response));
    }

    @PutMapping("/{partnerId}/deactivate")
    @Operation(summary = "Deactivate a partner",
               description = "Deactivates a partner. Their data is preserved. Deactivated partners cannot authenticate.")
    public ResponseEntity<ApiResponse<PartnerResponse>> deactivatePartner(@PathVariable UUID partnerId) {
        PartnerResponse response = partnerService.deactivatePartner(partnerId);
        return ResponseEntity.ok(ApiResponse.success("Partner deactivated", response));
    }

    @GetMapping("/{partnerId}")
    @Operation(summary = "Get partner by ID")
    public ResponseEntity<ApiResponse<PartnerResponse>> getPartner(@PathVariable UUID partnerId) {
        return ResponseEntity.ok(ApiResponse.success("Partner retrieved", partnerService.getPartnerById(partnerId)));
    }

    @GetMapping("/code/{partnerCode}")
    @Operation(summary = "Get partner by code")
    public ResponseEntity<ApiResponse<PartnerResponse>> getPartnerByCode(@PathVariable String partnerCode) {
        return ResponseEntity.ok(ApiResponse.success("Partner retrieved", partnerService.getPartnerByCode(partnerCode)));
    }

    @GetMapping
    @Operation(summary = "List all partners (paginated)",
               description = "Returns a paginated list of partners with optional filters by active status, type, or search term.")
    public ResponseEntity<PagedResponse<PartnerResponse>> listPartners(
            @Parameter(description = "Filter by active status") @RequestParam(required = false) Boolean active,
            @Parameter(description = "Filter by partner type") @RequestParam(required = false) PartnerType partnerType,
            @Parameter(description = "Search by name or code") @RequestParam(required = false) String search,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "DESC") String sortDir) {

        size = Math.min(size, 100);
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<PartnerResponse> partners = partnerService.listPartners(active, partnerType, search, pageable);

        return ResponseEntity.ok(PagedResponse.of(
                partners.getContent(), page, size,
                partners.getTotalElements(), sortBy, sortDir));
    }

    // ─── Partner-Insurer Mappings ─────────────────────────────────────────────

    @PostMapping("/{partnerId}/mappings")
    @Operation(summary = "Map a partner to an insurer",
               description = "Authorises a partner to submit requests for a specific insurer.")
    public ResponseEntity<ApiResponse<MappingResponse>> createMapping(
            @PathVariable UUID partnerId,
            @Valid @RequestBody CreateMappingRequest request) {

        String createdBy = "admin";
        MappingResponse response = partnerMappingService.createMapping(partnerId, request, createdBy);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Partner-insurer mapping created", response));
    }

    @GetMapping("/{partnerId}/mappings")
    @Operation(summary = "List all active insurer mappings for a partner")
    public ResponseEntity<ApiResponse<List<MappingResponse>>> getMappings(@PathVariable UUID partnerId) {
        List<MappingResponse> mappings = partnerMappingService.getMappingsForPartner(partnerId);
        return ResponseEntity.ok(ApiResponse.success("Mappings retrieved", mappings));
    }

    @DeleteMapping("/mappings/{mappingId}")
    @Operation(summary = "Deactivate a partner-insurer mapping",
               description = "Prevents the partner from submitting to this insurer. Historical records are preserved.")
    public ResponseEntity<ApiResponse<MappingResponse>> deactivateMapping(@PathVariable UUID mappingId) {
        MappingResponse response = partnerMappingService.deactivateMapping(mappingId);
        return ResponseEntity.ok(ApiResponse.success("Mapping deactivated", response));
    }
}
