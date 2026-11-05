package com.insurance.integration.insurer.controller;

import com.insurance.integration.common.enums.InsurerType;
import com.insurance.integration.common.response.ApiResponse;
import com.insurance.integration.common.response.PagedResponse;
import com.insurance.integration.insurer.dto.CreateInsurerRequest;
import com.insurance.integration.insurer.dto.InsurerResponse;
import com.insurance.integration.insurer.service.InsurerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin REST API for insurer management.
 * All endpoints require admin authentication.
 */
@RestController
@RequestMapping("/api/v1/admin/insurers")
@RequiredArgsConstructor
@Tag(name = "Insurer Management", description = "Admin APIs for managing insurance provider registrations")
public class InsurerController {

    private final InsurerService insurerService;

    @PostMapping
    @Operation(summary = "Register a new insurance provider",
               description = "Registers an insurer and their S3/SNS/webhook configuration. " +
                              "Sensitive config (IAM ARN, secrets) is stored server-side only.")
    public ResponseEntity<ApiResponse<InsurerResponse>> registerInsurer(
            @Valid @RequestBody CreateInsurerRequest request) {

        InsurerResponse response = insurerService.registerInsurer(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Insurer registered successfully", response));
    }

    @GetMapping("/{insurerId}")
    @Operation(summary = "Get insurer by ID")
    public ResponseEntity<ApiResponse<InsurerResponse>> getInsurer(@PathVariable UUID insurerId) {
        return ResponseEntity.ok(ApiResponse.success("Insurer retrieved", insurerService.getInsurerById(insurerId)));
    }

    @GetMapping("/code/{insurerCode}")
    @Operation(summary = "Get insurer by code")
    public ResponseEntity<ApiResponse<InsurerResponse>> getInsurerByCode(@PathVariable String insurerCode) {
        return ResponseEntity.ok(ApiResponse.success("Insurer retrieved", insurerService.getInsurerByCode(insurerCode)));
    }

    @GetMapping
    @Operation(summary = "List all insurers (paginated)")
    public ResponseEntity<PagedResponse<InsurerResponse>> listInsurers(
            @Parameter(description = "Filter by active status") @RequestParam(required = false) Boolean active,
            @Parameter(description = "Filter by insurer type") @RequestParam(required = false) InsurerType insurerType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        size = Math.min(size, 100);
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<InsurerResponse> insurers = insurerService.listInsurers(active, insurerType, pageable);
        return ResponseEntity.ok(PagedResponse.of(
                insurers.getContent(), page, size,
                insurers.getTotalElements(), sortBy, sortDir));
    }

    @PutMapping("/{insurerId}/activate")
    @Operation(summary = "Activate an insurer")
    public ResponseEntity<ApiResponse<InsurerResponse>> activateInsurer(@PathVariable UUID insurerId) {
        return ResponseEntity.ok(ApiResponse.success("Insurer activated", insurerService.activateInsurer(insurerId)));
    }

    @PutMapping("/{insurerId}/deactivate")
    @Operation(summary = "Deactivate an insurer")
    public ResponseEntity<ApiResponse<InsurerResponse>> deactivateInsurer(@PathVariable UUID insurerId) {
        return ResponseEntity.ok(ApiResponse.success("Insurer deactivated", insurerService.deactivateInsurer(insurerId)));
    }
}
