package com.insurance.integration.schema.controller;

import com.insurance.integration.common.response.ApiResponse;
import com.insurance.integration.schema.dto.ValidationResultDTO;
import com.insurance.integration.schema.service.ValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Validation trigger APIs (Module 4).
 *
 * <p>Allows an administrator to (re-)run schema validation against a submission and
 * persist the result.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/insurance/submissions")
@RequiredArgsConstructor
@Tag(name = "Validation (Admin)", description = "Admin APIs for triggering and retrieving schema validation results")
public class ValidationController {

    private final ValidationService validationService;

    @PostMapping("/{submissionId}/validate")
    @Operation(summary = "Trigger validation for a submission",
            description = "Runs the configurable schema engine against a submission and persists the result.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Validation completed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Submission not found")
    })
    public ResponseEntity<ApiResponse<ValidationResultDTO>> validateSubmission(@PathVariable UUID submissionId) {
        ValidationResultDTO result = validationService.validateSubmission(submissionId);
        return ResponseEntity.ok(ApiResponse.success("Validation completed", result));
    }
}
