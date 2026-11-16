package com.insurance.integration.schema.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.integration.common.exception.ResourceNotFoundException;
import com.insurance.integration.common.exception.ValidationException;
import com.insurance.integration.ingestion.dto.DocumentMetadataDTO;
import com.insurance.integration.ingestion.entity.IngestionDocument;
import com.insurance.integration.ingestion.entity.IngestionRequest;
import com.insurance.integration.ingestion.repository.IngestionDocumentRepository;
import com.insurance.integration.ingestion.repository.IngestionRequestRepository;
import com.insurance.integration.schema.dto.DocumentRuleDTO;
import com.insurance.integration.schema.dto.FieldRuleDTO;
import com.insurance.integration.schema.dto.ValidationResultDTO;
import com.insurance.integration.schema.entity.ProductSchema;
import com.insurance.integration.schema.repository.ProductSchemaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates schema validation for an ingestion request (Module 4).
 *
 * <p>Selects the applicable schema, runs the validation engine, persists the result and
 * updates the submission status.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationService {

    private final IngestionRequestRepository requestRepository;
    private final IngestionDocumentRepository documentRepository;
    private final SchemaService schemaService;
    private final ValidationEngine validationEngine;
    private final ObjectMapper objectMapper;

    /**
     * Trigger validation for an existing submission and persist the outcome.
     */
    @Transactional
    public ValidationResultDTO validateSubmission(UUID submissionId) {
        IngestionRequest request = requestRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", submissionId.toString()));

        request.setStatus(com.insurance.integration.common.enums.IngestionStatus.VALIDATING);
        requestRepository.save(request);

        // Select the schema used at ingestion time, falling back to a fresh selection.
        ProductSchema schema = schemaService.selectSchema(
                request.getPartnerId(), request.getInsurerId(), request.getProductType(), null);
        if (schema == null) {
            throw new ValidationException("No active schema found for product " + request.getProductType());
        }
        request.setSchemaId(schema.getId());

        List<FieldRuleDTO> fieldRules = schemaService.listFieldRules(schema.getId());
        List<DocumentRuleDTO> documentRules = schemaService.listDocumentRules(schema.getId());

        List<IngestionDocument> docs = documentRepository.findByIngestionRequestId(submissionId);
        List<ValidationEngine.IngestionDocumentRef> docRefs = new ArrayList<>();
        for (IngestionDocument d : docs) {
            docRefs.add(new ValidationEngine.IngestionDocumentRef(
                    d.getDocumentType(), d.getOriginalFilename(), d.getDeclaredMimeType()));
        }

        ValidationResultDTO result = validationEngine.validate(
                submissionId, schema, fieldRules, documentRules,
                request.getCustomerData(), request.getProductData(), docRefs);

        // Persist the validation result on the submission.
        try {
            request.setValidationResult(objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            throw new ValidationException("Failed to serialise validation result", List.of());
        }
        request.setValidationErrorCount(result.getFailedChecks());
        request.setValidatedAt(Instant.now());
        request.setStatus(result.getFailedChecks() == 0
                ? com.insurance.integration.common.enums.IngestionStatus.VALIDATED
                : com.insurance.integration.common.enums.IngestionStatus.VALIDATION_FAILED);
        requestRepository.save(request);

        log.info("Validation persisted for submission={} status={} errors={}",
                submissionId, request.getStatus(), result.getFailedChecks());
        return result;
    }

    /**
     * Retrieve the persisted validation result for a submission.
     */
    public ValidationResultDTO getValidationResult(UUID submissionId) {
        IngestionRequest request = requestRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", submissionId.toString()));
        if (request.getValidationResult() == null) {
            throw new ResourceNotFoundException("Validation result", submissionId.toString());
        }
        try {
            return objectMapper.readValue(request.getValidationResult(), ValidationResultDTO.class);
        } catch (Exception e) {
            throw new ValidationException("Failed to read validation result", List.of());
        }
    }

    /**
     * Build sanitised document metadata DTOs for a submission (URLs excluded).
     */
    public List<DocumentMetadataDTO> listDocumentMetadata(UUID submissionId) {
        List<IngestionDocument> docs = documentRepository.findByIngestionRequestId(submissionId);
        return docs.stream().map(d -> DocumentMetadataDTO.builder()
                .documentId(d.getId())
                .documentType(d.getDocumentType())
                .fileName(d.getOriginalFilename())
                .declaredMimeType(d.getDeclaredMimeType())
                .declaredSizeBytes(d.getDeclaredSizeBytes())
                .documentStatus(d.getDocumentStatus().name())
                .createdAt(d.getCreatedAt())
                .build()).collect(Collectors.toList());
    }
}
