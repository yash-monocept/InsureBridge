package com.insurance.integration.schema.controller;

import com.insurance.integration.common.response.ApiResponse;
import com.insurance.integration.common.response.PagedResponse;
import com.insurance.integration.schema.dto.DocumentRuleCreateRequest;
import com.insurance.integration.schema.dto.DocumentRuleDTO;
import com.insurance.integration.schema.dto.FieldRuleCreateRequest;
import com.insurance.integration.schema.dto.FieldRuleDTO;
import com.insurance.integration.schema.dto.SchemaCreateRequest;
import com.insurance.integration.schema.dto.SchemaDTO;
import com.insurance.integration.schema.dto.SchemaUpdateRequest;
import com.insurance.integration.schema.service.SchemaService;
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
 * Schema administration APIs (Module 4 - Configurable Schema and Tag Validation).
 *
 * <p>These endpoints are admin-only in production. They are exposed without a separate
 * admin identity in this academic build, consistent with the existing {@code /api/v1/admin/**}
 * convention.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/schemas")
@RequiredArgsConstructor
@Tag(name = "Schema Management (Admin)", description = "Administrative APIs for insurance data schemas and validation rules")
public class SchemaController {

    private final SchemaService schemaService;

    @PostMapping
    @Operation(summary = "Create a schema",
            description = "Creates a new product schema version with the given insurer/product scope.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Schema created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Schema version already exists")
    })
    public ResponseEntity<ApiResponse<SchemaDTO>> createSchema(
            @Valid @RequestBody SchemaCreateRequest request) {
        SchemaDTO created = schemaService.createSchema(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Schema created", created));
    }

    @GetMapping
    @Operation(summary = "List schemas",
            description = "Returns a paginated list of schemas with optional filters.")
    public ResponseEntity<PagedResponse<SchemaDTO>> listSchemas(
            @Parameter(description = "Filter by insurer ID") @RequestParam(required = false) UUID insurerId,
            @Parameter(description = "Filter by partner ID") @RequestParam(required = false) UUID partnerId,
            @Parameter(description = "Filter by product type") @RequestParam(required = false) String productType,
            @Parameter(description = "Filter by active status") @RequestParam(required = false) Boolean active,
            @Parameter(description = "Search by schema name") @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var page = schemaService.listSchemas(insurerId, partnerId, productType, active, search, pageable);
        return ResponseEntity.ok(PagedResponse.of(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getSort().toString(), "DESC"));
    }

    @GetMapping("/{schemaId}")
    @Operation(summary = "Get a schema by ID")
    public ResponseEntity<ApiResponse<SchemaDTO>> getSchema(@PathVariable UUID schemaId) {
        return ResponseEntity.ok(ApiResponse.success("Schema retrieved", schemaService.getSchema(schemaId)));
    }

    @PutMapping("/{schemaId}")
    @Operation(summary = "Update a schema",
            description = "Updates the name/active flag of an existing schema version. Historical versions are not replaced.")
    public ResponseEntity<ApiResponse<SchemaDTO>> updateSchema(@PathVariable UUID schemaId,
                                                               @Valid @RequestBody SchemaUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Schema updated", schemaService.updateSchema(schemaId, request)));
    }

    @PostMapping("/{schemaId}/versions")
    @Operation(summary = "Create a new schema version",
            description = "Alias for creating a schema; a new version is always inserted as a distinct row.")
    public ResponseEntity<ApiResponse<SchemaDTO>> createVersion(@PathVariable UUID schemaId,
                                                                @Valid @RequestBody SchemaCreateRequest request) {
        // Versioning is expressed via schema_version on a new row; the schemaId path variable
        // is accepted for routing compatibility but the new row carries its own ID.
        SchemaDTO created = schemaService.createSchema(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Schema version created", created));
    }

    @GetMapping("/{schemaId}/versions")
    @Operation(summary = "List schema versions",
            description = "Returns all schema versions matching the schema's product type.")
    public ResponseEntity<ApiResponse<List<SchemaDTO>>> listVersions(@PathVariable UUID schemaId) {
        SchemaDTO schema = schemaService.getSchema(schemaId);
        var page = schemaService.listSchemas(null, null, schema.getProductType(), null, null,
                org.springframework.data.domain.Pageable.unpaged());
        return ResponseEntity.ok(ApiResponse.success("Schema versions retrieved", page.getContent()));
    }

    @GetMapping("/{schemaId}/rules")
    @Operation(summary = "List field rules for a schema")
    public ResponseEntity<ApiResponse<List<FieldRuleDTO>>> listFieldRules(@PathVariable UUID schemaId) {
        return ResponseEntity.ok(ApiResponse.success("Field rules retrieved", schemaService.listFieldRules(schemaId)));
    }

    @PostMapping("/{schemaId}/rules")
    @Operation(summary = "Create a field rule",
            description = "Adds a field validation rule to the schema.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Field rule created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Field path already exists")
    })
    public ResponseEntity<ApiResponse<FieldRuleDTO>> createFieldRule(@PathVariable UUID schemaId,
                                                                     @Valid @RequestBody FieldRuleCreateRequest request) {
        FieldRuleDTO created = schemaService.createFieldRule(schemaId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Field rule created", created));
    }

    @GetMapping("/{schemaId}/document-rules")
    @Operation(summary = "List document rules for a schema")
    public ResponseEntity<ApiResponse<List<DocumentRuleDTO>>> listDocumentRules(@PathVariable UUID schemaId) {
        return ResponseEntity.ok(ApiResponse.success("Document rules retrieved", schemaService.listDocumentRules(schemaId)));
    }

    @PostMapping("/{schemaId}/document-rules")
    @Operation(summary = "Create a document rule",
            description = "Adds a document requirement rule to the schema.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Document rule created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Document type already exists")
    })
    public ResponseEntity<ApiResponse<DocumentRuleDTO>> createDocumentRule(@PathVariable UUID schemaId,
                                                                           @Valid @RequestBody DocumentRuleCreateRequest request) {
        DocumentRuleDTO created = schemaService.createDocumentRule(schemaId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Document rule created", created));
    }
}
