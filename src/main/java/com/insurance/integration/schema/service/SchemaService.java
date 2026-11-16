package com.insurance.integration.schema.service;

import com.insurance.integration.common.exception.DuplicateResourceException;
import com.insurance.integration.common.exception.ResourceNotFoundException;
import com.insurance.integration.schema.dto.DocumentRuleCreateRequest;
import com.insurance.integration.schema.dto.DocumentRuleDTO;
import com.insurance.integration.schema.dto.FieldRuleCreateRequest;
import com.insurance.integration.schema.dto.FieldRuleDTO;
import com.insurance.integration.schema.dto.SchemaCreateRequest;
import com.insurance.integration.schema.dto.SchemaDTO;
import com.insurance.integration.schema.dto.SchemaUpdateRequest;
import com.insurance.integration.schema.entity.DocumentRule;
import com.insurance.integration.schema.entity.FieldRule;
import com.insurance.integration.schema.entity.ProductSchema;
import com.insurance.integration.schema.repository.DocumentRuleRepository;
import com.insurance.integration.schema.repository.FieldRuleRepository;
import com.insurance.integration.schema.repository.ProductSchemaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing insurance data schemas and their validation rules (Module 4).
 *
 * <p>Historical schema versions are never modified in place — a new version is created
 * instead, so previously validated submissions remain traceable to the exact schema used.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SchemaService {

    private final ProductSchemaRepository schemaRepository;
    private final FieldRuleRepository fieldRuleRepository;
    private final DocumentRuleRepository documentRuleRepository;

    /**
     * Create a new schema version.
     */
    @Transactional
    public SchemaDTO createSchema(SchemaCreateRequest request) {
        boolean duplicate = schemaRepository.findWithFilters(
                request.getInsurerId(), request.getPartnerId(), request.getProductType(),
                null, null, org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().stream()
                .anyMatch(s -> request.getSchemaVersion().equals(s.getSchemaVersion()));
        if (duplicate) {
            throw new DuplicateResourceException("Schema", "schemaVersion",
                    request.getProductType() + ":" + request.getSchemaVersion());
        }

        ProductSchema schema = ProductSchema.builder()
                .insurerId(request.getInsurerId())
                .partnerId(request.getPartnerId())
                .productType(request.getProductType())
                .schemaVersion(request.getSchemaVersion())
                .schemaName(request.getSchemaName())
                .active(request.getActive() != null && request.getActive())
                .build();
        schema = schemaRepository.save(schema);
        log.info("Schema created: product={}, version={}, id={}",
                schema.getProductType(), schema.getSchemaVersion(), schema.getId());
        return toDto(schema);
    }

    /**
     * Get a schema by ID.
     */
    public SchemaDTO getSchema(UUID schemaId) {
        ProductSchema schema = schemaRepository.findByIdAndActive(schemaId, true)
                .orElseThrow(() -> new ResourceNotFoundException("Schema", schemaId.toString()));
        return toDto(schema);
    }

    /**
     * List schemas with optional filters and pagination.
     */
    public Page<SchemaDTO> listSchemas(UUID insurerId, UUID partnerId, String productType,
                                       Boolean active, String search, Pageable pageable) {
        return schemaRepository.findWithFilters(insurerId, partnerId, productType, active, search, pageable)
                .map(this::toDto);
    }

    /**
     * Update a schema's name/active flag. Historical versions are not replaced.
     */
    @Transactional
    public SchemaDTO updateSchema(UUID schemaId, SchemaUpdateRequest request) {
        ProductSchema schema = schemaRepository.findByIdAndActive(schemaId, true)
                .orElseThrow(() -> new ResourceNotFoundException("Schema", schemaId.toString()));
        if (request.getSchemaName() != null) {
            schema.setSchemaName(request.getSchemaName());
        }
        if (request.getActive() != null) {
            schema.setActive(request.getActive());
        }
        schema = schemaRepository.save(schema);
        log.info("Schema updated: id={}", schema.getId());
        return toDto(schema);
    }

    /**
     * Select the most specific active schema for a partner/insurer/product combination.
     * Precedence: partner+insurer > partner-only > insurer-only > global (both NULL).
     * When a specific schemaVersion is requested it is matched exactly.
     */
    public ProductSchema selectSchema(UUID partnerId, UUID insurerId, String productType,
                                      String schemaVersion) {
        List<ProductSchema> candidates = schemaRepository.findByProductTypeAndActive(productType, true);
        if (candidates.isEmpty()) {
            return null;
        }
        ProductSchema best = null;
        int bestScore = -1;
        for (ProductSchema s : candidates) {
            if (schemaVersion != null && !schemaVersion.equals(s.getSchemaVersion())) {
                continue;
            }
            int score = 0;
            if (partnerId != null && partnerId.equals(s.getPartnerId())) score += 4;
            if (insurerId != null && insurerId.equals(s.getInsurerId())) score += 2;
            if (partnerId == null && s.getPartnerId() == null) score += 1;
            if (insurerId == null && s.getInsurerId() == null) score += 1;
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    /**
     * Add a field rule to a schema.
     */
    @Transactional
    public FieldRuleDTO createFieldRule(UUID schemaId, FieldRuleCreateRequest request) {
        ProductSchema schema = schemaRepository.findByIdAndActive(schemaId, true)
                .orElseThrow(() -> new ResourceNotFoundException("Schema", schemaId.toString()));
        if (fieldRuleRepository.existsBySchemaIdAndFieldPath(schemaId, request.getFieldPath())) {
            throw new DuplicateResourceException("FieldRule", "fieldPath", request.getFieldPath());
        }

        FieldRule rule = FieldRule.builder()
                .schemaId(schemaId)
                .fieldPath(request.getFieldPath())
                .displayName(request.getDisplayName())
                .required(request.getRequired() != null && request.getRequired())
                .dataType(request.getDataType())
                .minLength(request.getMinLength())
                .maxLength(request.getMaxLength())
                .minValue(request.getMinValue())
                .maxValue(request.getMaxValue())
                .regexPattern(request.getRegexPattern())
                .allowedValues(request.getAllowedValues())
                .dateFormat(request.getDateFormat())
                .errorMessage(request.getErrorMessage())
                .build();
        rule = fieldRuleRepository.save(rule);
        log.info("Field rule created: schema={}, field={}", schemaId, request.getFieldPath());
        return toDto(rule);
    }

    /**
     * List field rules for a schema.
     */
    public List<FieldRuleDTO> listFieldRules(UUID schemaId) {
        if (!schemaRepository.existsById(schemaId)) {
            throw new ResourceNotFoundException("Schema", schemaId.toString());
        }
        return fieldRuleRepository.findBySchemaId(schemaId).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Add a document rule to a schema.
     */
    @Transactional
    public DocumentRuleDTO createDocumentRule(UUID schemaId, DocumentRuleCreateRequest request) {
        ProductSchema schema = schemaRepository.findByIdAndActive(schemaId, true)
                .orElseThrow(() -> new ResourceNotFoundException("Schema", schemaId.toString()));
        if (documentRuleRepository.existsBySchemaIdAndDocumentType(schemaId, request.getDocumentType())) {
            throw new DuplicateResourceException("DocumentRule", "documentType", request.getDocumentType());
        }

        DocumentRule rule = DocumentRule.builder()
                .schemaId(schemaId)
                .documentType(request.getDocumentType())
                .required(request.getRequired() != null && request.getRequired())
                .allowedMimeTypes(request.getAllowedMimeTypes())
                .maxSizeMb(request.getMaxSizeMb() != null ? request.getMaxSizeMb() : 10)
                .description(request.getDescription())
                .build();
        rule = documentRuleRepository.save(rule);
        log.info("Document rule created: schema={}, type={}", schemaId, request.getDocumentType());
        return toDto(rule);
    }

    /**
     * List document rules for a schema.
     */
    public List<DocumentRuleDTO> listDocumentRules(UUID schemaId) {
        if (!schemaRepository.existsById(schemaId)) {
            throw new ResourceNotFoundException("Schema", schemaId.toString());
        }
        return documentRuleRepository.findBySchemaId(schemaId).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    // ─── mappers ──────────────────────────────────────────────────────────

    private SchemaDTO toDto(ProductSchema schema) {
        return SchemaDTO.builder()
                .id(schema.getId())
                .insurerId(schema.getInsurerId())
                .partnerId(schema.getPartnerId())
                .productType(schema.getProductType())
                .schemaVersion(schema.getSchemaVersion())
                .schemaName(schema.getSchemaName())
                .active(schema.isActive())
                .createdAt(schema.getCreatedAt())
                .updatedAt(schema.getUpdatedAt())
                .build();
    }

    private FieldRuleDTO toDto(FieldRule rule) {
        return FieldRuleDTO.builder()
                .id(rule.getId())
                .schemaId(rule.getSchemaId())
                .fieldPath(rule.getFieldPath())
                .displayName(rule.getDisplayName())
                .required(rule.isRequired())
                .dataType(rule.getDataType())
                .minLength(rule.getMinLength())
                .maxLength(rule.getMaxLength())
                .minValue(rule.getMinValue())
                .maxValue(rule.getMaxValue())
                .regexPattern(rule.getRegexPattern())
                .allowedValues(rule.getAllowedValues())
                .dateFormat(rule.getDateFormat())
                .errorMessage(rule.getErrorMessage())
                .build();
    }

    private DocumentRuleDTO toDto(DocumentRule rule) {
        return DocumentRuleDTO.builder()
                .id(rule.getId())
                .schemaId(rule.getSchemaId())
                .documentType(rule.getDocumentType())
                .required(rule.isRequired())
                .allowedMimeTypes(rule.getAllowedMimeTypes())
                .maxSizeMb(rule.getMaxSizeMb())
                .description(rule.getDescription())
                .build();
    }
}
