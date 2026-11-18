package com.insurance.integration.ingestion.controller;

import com.insurance.integration.common.response.ApiResponse;
import com.insurance.integration.common.response.PagedResponse;
import com.insurance.integration.ingestion.dto.IngestionRequestDTO;
import com.insurance.integration.ingestion.dto.IngestionResponseDTO;
import com.insurance.integration.ingestion.dto.SubmissionDetailDTO;
import com.insurance.integration.ingestion.dto.SubmissionSummaryDTO;
import com.insurance.integration.ingestion.service.IngestionService;
import com.insurance.integration.insurer.repository.InsurerRepository;
import com.insurance.integration.security.principal.PartnerPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.web.bind.annotation.AuthenticationPrincipal;

import java.util.UUID;

/**
 * Partner-facing insurance submission APIs (Module 3 - Request Ingestion).
 *
 * <p>All partner endpoints require a valid JWT and are scoped to the authenticated partner.
 * Admin endpoints list submissions across all partners.</p>
 */
@RestController
@RequestMapping("/api/v1/partner/insurance/submissions")
@RequiredArgsConstructor
@Tag(name = "Insurance Submissions (Partner)", description = "Partner-facing insurance application ingestion")
public class IngestionController {

    private final IngestionService ingestionService;
    private final InsurerRepository insurerRepository;

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_write')")
    @Operation(summary = "Submit an insurance application",
            description = "Authenticated partners submit an insurance application with document references.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Submission created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not authorised for insurer/product"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate idempotency key")
    })
    public ResponseEntity<ApiResponse<IngestionResponseDTO>> submit(
            @AuthenticationPrincipal PartnerPrincipal principal,
            @Valid @RequestBody IngestionRequestDTO request) {

        IngestionResponseDTO response = ingestionService.submit(request, principal,
                null, null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Submission received", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_read')")
    @Operation(summary = "List the authenticated partner's submissions",
            description = "Returns a paginated list of submissions belonging to the authenticated partner.")
    public ResponseEntity<PagedResponse<SubmissionSummaryDTO>> listPartnerSubmissions(
            @AuthenticationPrincipal PartnerPrincipal principal,
            @Parameter(description = "Filter by insurer code") @RequestParam(required = false) String insurerCode,
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            @Parameter(description = "Filter by product type") @RequestParam(required = false) String productType,
            @Parameter(description = "Search by reference") @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        // Resolve insurer code to id if provided
        java.util.UUID insurerId = null;
        if (insurerCode != null && !insurerCode.isBlank()) {
            insurerId = resolveInsurerId(insurerCode);
        }
        com.insurance.integration.common.enums.IngestionStatus statusEnum = parseStatus(status);
        var page = ingestionService.listPartnerSubmissions(principal.partnerId(), insurerId,
                statusEnum, productType, search, pageable);
        return ResponseEntity.ok(PagedResponse.of(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getSort().toString(), "DESC"));
    }

    @GetMapping("/{submissionId}")
    @PreAuthorize("hasAuthority('SCOPE_read')")
    @Operation(summary = "Get a submission by ID",
            description = "Returns a detailed view of one of the authenticated partner's submissions.")
    public ResponseEntity<ApiResponse<SubmissionDetailDTO>> getPartnerSubmission(
            @AuthenticationPrincipal PartnerPrincipal principal,
            @PathVariable UUID submissionId) {
        SubmissionDetailDTO detail = ingestionService.getPartnerSubmission(principal.partnerId(), submissionId);
        return ResponseEntity.ok(ApiResponse.success("Submission retrieved", detail));
    }

    @GetMapping("/{submissionId}/validation")
    @PreAuthorize("hasAuthority('SCOPE_read')")
    @Operation(summary = "Get validation results for a submission",
            description = "Returns the schema validation result for one of the authenticated partner's submissions.")
    public ResponseEntity<ApiResponse<com.insurance.integration.schema.dto.ValidationResultDTO>>
    getPartnerSubmissionValidation(@AuthenticationPrincipal PartnerPrincipal principal,
                                   @PathVariable UUID submissionId) {
        com.insurance.integration.schema.dto.ValidationResultDTO result =
                ingestionService.getPartnerSubmissionValidation(principal.partnerId(), submissionId);
        return ResponseEntity.ok(ApiResponse.success("Validation result retrieved", result));
    }

    // ─── Admin endpoints ─────────────────────────────────────────────────

    @GetMapping("/admin/all")
    @Operation(summary = "List all submissions (admin)",
            description = "Admin-only: returns a paginated list of all submissions across partners.")
    public ResponseEntity<PagedResponse<SubmissionSummaryDTO>> listAllSubmissions(
            @Parameter(description = "Filter by insurer code") @RequestParam(required = false) String insurerCode,
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            @Parameter(description = "Filter by product type") @RequestParam(required = false) String productType,
            @Parameter(description = "Search by reference") @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        java.util.UUID insurerId = null;
        if (insurerCode != null && !insurerCode.isBlank()) {
            insurerId = resolveInsurerId(insurerCode);
        }
        com.insurance.integration.common.enums.IngestionStatus statusEnum = parseStatus(status);
        var page = ingestionService.listAllSubmissions(insurerId, statusEnum, productType, search, pageable);
        return ResponseEntity.ok(PagedResponse.of(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getSort().toString(), "DESC"));
    }

    @GetMapping("/admin/{submissionId}")
    @Operation(summary = "Get a submission by ID (admin)",
            description = "Admin-only: returns a detailed view of any submission.")
    public ResponseEntity<ApiResponse<SubmissionDetailDTO>> getAdminSubmission(
            @PathVariable UUID submissionId) {
        SubmissionDetailDTO detail = ingestionService.getAdminSubmission(submissionId);
        return ResponseEntity.ok(ApiResponse.success("Submission retrieved", detail));
    }

    @GetMapping("/admin/{submissionId}/validation")
    @Operation(summary = "Get validation results for a submission (admin)",
            description = "Admin-only: returns the schema validation result for any submission.")
    public ResponseEntity<ApiResponse<com.insurance.integration.schema.dto.ValidationResultDTO>>
    getAdminSubmissionValidation(@PathVariable UUID submissionId) {
        com.insurance.integration.schema.dto.ValidationResultDTO result =
                ingestionService.getAdminSubmissionValidation(submissionId);
        return ResponseEntity.ok(ApiResponse.success("Validation result retrieved", result));
    }

    // ─── helpers ─────────────────────────────────────────────────────────

    private java.util.UUID resolveInsurerId(String insurerCode) {
        if (insurerCode == null || insurerCode.isBlank()) {
            return null;
        }
        return insurerRepository.findByInsurerCode(insurerCode)
                .map(com.insurance.integration.insurer.entity.Insurer::getId)
                .orElse(null);
    }

    private com.insurance.integration.common.enums.IngestionStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return com.insurance.integration.common.enums.IngestionStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
